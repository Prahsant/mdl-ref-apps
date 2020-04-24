/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.fragment

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.FLAG_READER_NFC_A
import android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.bleofflinetransfer.utils.BleUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.model.UserCredential
import com.ul.ims.gmdl.cbordata.response.BleTransferResponse
import com.ul.ims.gmdl.databinding.FragmentOfflineTransferStatusBinding
import com.ul.ims.gmdl.dialog.CustomAlertDialog
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.offlinetransfer.utils.Resource
import com.ul.ims.gmdl.viewmodel.OfflineTransferStatusViewModel
import kotlinx.android.synthetic.main.fragment_offline_transfer_status.*
import java.util.*


/**
 * A simple [Fragment] subclass.
 *
 */
class OfflineTransferStatusFragment : Fragment() {

    companion object {
        const val LOG_TAG = "OfflineTransferStatusFragment"
    }

    private lateinit var vm : OfflineTransferStatusViewModel
    private var deviceEngagement :  DeviceEngagement? = null
    private var requestItems : Array<String>? = null
    private var transferMethod: TransferChannels? = null
    private var bleServiceMode : BleServiceMode? = null
    private var wifiPassphrase: String? = null
    private val REQUEST_ENABLE_BT = 7890

    private var nfcAdapter: NfcAdapter? = null
    private val READER_FLAG = (NfcAdapter.FLAG_READER_NFC_A
            or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val args : OfflineTransferStatusFragmentArgs by navArgs()
        deviceEngagement = args.deviceEngagement
        requestItems = args.requestItems
        transferMethod = args.transferMethod
        bleServiceMode = args.bleServiceMode
        wifiPassphrase = args.wifiPassphrase

        vm = ViewModelProvider(this).get(OfflineTransferStatusViewModel::class.java)

        val binding = FragmentOfflineTransferStatusBinding.inflate(inflater)
        binding.fragment = this

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val options = Bundle()
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000)

        nfcAdapter?.enableReaderMode(activity,
                                    ReaderModeCallback(),
                                    READER_FLAG,
                                    options)
    }

    override fun onPause() {
        super.onPause()

        nfcAdapter?.disableReaderMode(activity)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (transferMethod) {
            TransferChannels.BLE -> {
                // not sure if here is the best place to setup the verifier
                if (BleUtils.isBtEnabled(requireContext())) {
                    setupBleVerifier()
                } else {
                    requestToTurnOnBle()
                }
            }
            TransferChannels.WiFiAware -> {
                setupWiFiVerifier()
            }
            TransferChannels.NFC -> {
                //NFC setup is done once NFC Tag is discovered
            }
        }
    }

    private fun setupBleVerifier() {
        deviceEngagement?.let {de ->
            requestItems?.let { req ->
                bleServiceMode?.let { bsm ->
                    vm.setupBleVerifier(de, req, bsm)
                } ?: kotlin.run {
                    Log.e(LOG_TAG, "BLE Service Mode is null")
                }
            } ?: kotlin.run {
                Log.e(LOG_TAG, "Data Items List is null")
            }
        } ?: kotlin.run {
            Log.e(LOG_TAG, "Device Engagement is null")
        }
    }

    private fun setupWiFiVerifier() {
        deviceEngagement?.let {de ->
            requestItems?.let { req ->
                vm.setupWiFiVerifier(de, req, wifiPassphrase)
            } ?: kotlin.run {
                Log.e(LOG_TAG, "Data Items List is null")
            }
        } ?: kotlin.run {
            Log.e(LOG_TAG, "Device Engagement is null")
        }
    }

    private fun setupNfcVerifier(nfcTag: IsoDep) {
        deviceEngagement?.let {de ->
            requestItems?.let { req ->
                vm.setupNfcVerifier(de, req, nfcTag)
            } ?: kotlin.run {
                Log.e(LOG_TAG, "Data Items List is null")
            }
        } ?: kotlin.run {
            Log.e(LOG_TAG, "Device Engagement is null")
        }
    }

    override fun onStart() {
        super.onStart()

        vm.getTransferData()?.observe(this, Observer {res->
            when(res?.status) {
                Resource.Status.CONNECTING -> {
                    progress_connecting.visibility = View.VISIBLE
                    img_conn_status.visibility = View.INVISIBLE
                }
                Resource.Status.TRANSFERRING -> {
                    img_conn_status.setImageResource(R.drawable.ic_baseline_done)
                    img_conn_status.visibility = View.VISIBLE
                    progress_connecting.visibility = View.INVISIBLE
                    progress_tranferring_data.visibility = View.VISIBLE
                }

                Resource.Status.NO_DEVICE_FOUND -> {
                    onErrorUpdateUi()
                }

                Resource.Status.TRANSFER_SUCCESSFUL -> {
                    img_conn_status.setImageResource(R.drawable.ic_baseline_done)
                    img_conn_status.visibility = View.VISIBLE
                    progress_connecting.visibility = View.INVISIBLE
                    progress_tranferring_data.visibility = View.INVISIBLE
                    img_transfer_status.setImageResource(R.drawable.ic_baseline_done)
                    img_transfer_status.visibility = View.VISIBLE

                    val response = res.data as? BleTransferResponse
                    response?.let {r->
                        if (r.responseStatus != 0 || r.isEmpty()) {
                            Log.d(LOG_TAG, "Response Error")
                            CustomAlertDialog(requireContext())
                            { findNavController().popBackStack() }.showErrorDialog(getString(
                                R.string.response_error),
                                getString(R.string.response_errors_details))
                        } else {
                            val credential = UserCredential.Builder()
                                .fromBleTransferResponse(r)
                                .setDeviceSign(r.deviceSignValidation)
                                .setIssuerDataAuthentication(r.issuerDataValidation)
                                .build()

                            val action = OfflineTransferStatusFragmentDirections.
                                actionOfflineTransferStatusFragmentToDisplayCredentialsFragment2(credential)

                            findNavController().navigate(action)
                        }
                    }
                }
                Resource.Status.TRANSFER_ERROR -> {
                    val errorMsg = res.message
                    CustomAlertDialog(requireContext())
                    { findNavController().popBackStack() }.showErrorDialog(getString(
                        R.string.connection_setup_error), errorMsg)
                    onErrorUpdateUi()
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()

        vm.tearDown()
    }

    fun stopTransfer(view: View?) {
        findNavController().popBackStack()
    }

    private fun onErrorUpdateUi() {
        if (img_conn_status.visibility == View.INVISIBLE) {
            img_conn_status.setImageResource(R.drawable.ic_baseline_error_outline)
            img_conn_status.visibility = View.VISIBLE
            progress_connecting.visibility = View.INVISIBLE
            progress_tranferring_data.visibility = View.INVISIBLE
        } else {
            progress_tranferring_data.visibility = View.INVISIBLE
            img_transfer_status.setImageResource(R.drawable.ic_baseline_error_outline)
            img_transfer_status.visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                when (resultCode) {
                    -1 -> {
                        // Update UI
                        setupWiFiVerifier()
                    }

                    0 -> {
                        requestToTurnOnBle()
                    }
                    else -> {
                        // Unknown resultCode
                        Log.e(ShareCredentialsFragment.LOG_TAG, "Unknown resultCode $resultCode")
                    }
                }
            }
        }
    }

    fun requestToTurnOnBle() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    private inner class VerifierSetupTask : AsyncTask<IsoDep, Void, Void>() {

        override fun doInBackground(vararg params: IsoDep): Void? {
            val tag = params[0]
            setupNfcVerifier(tag)
            return null
        }
    }
    private inner class ReaderModeCallback : NfcAdapter.ReaderCallback {

        override fun onTagDiscovered(tag: Tag) {

            // Tell the adapter to ignore this tag for any consecutive reads. debounceMs needs to be
            //  greater than the polling interval of the adapter
            nfcAdapter!!.ignore(tag, 5000, null, null)

            val techList = tag.techList

            Log.d(this.javaClass.simpleName, "Tag discovered: supported tech=" + Arrays.toString(techList))

            for (tech in techList) {
                if (IsoDep::class.java.name == tech) {
                    var isoDep = IsoDep.get(tag)
                    VerifierSetupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, isoDep)
                    break
                }
            }
        }
    }
}