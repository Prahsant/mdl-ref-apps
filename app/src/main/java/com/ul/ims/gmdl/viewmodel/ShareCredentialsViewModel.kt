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

package com.ul.ims.gmdl.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import androidx.biometric.BiometricPrompt
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import androidx.security.identity.IdentityCredentialException
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.bleofflinetransfer.utils.BleUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.Security
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleTransferMethod
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.WiFiAwareTransferMethod
import com.ul.ims.gmdl.cbordata.model.UserCredential.Companion.CREDENTIAL_NAME
import com.ul.ims.gmdl.issuerauthority.MockIssuerAuthority
import com.ul.ims.gmdl.offlineTransfer.OfflineTransferManager
import com.ul.ims.gmdl.offlinetransfer.appLayer.IofflineTransfer
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.data.DataTypes
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.offlinetransfer.utils.Resource
import com.ul.ims.gmdl.provisioning.ProvisioningManager
import com.ul.ims.gmdl.qrcode.MdlQrCode
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager
import com.ul.ims.gmdl.util.SharedPreferenceUtils
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class ShareCredentialsViewModel(val app: Application) : AndroidViewModel(app) {

    val LOG_TAG = ShareCredentialsViewModel::class.java.simpleName

    companion object {
        const val QRCODE_HEIGHT = 400
        const val QRCODE_WIDTH = 400
        const val BLE_VERSION = 1
        const val WIFI_AWARE_VERSION = 1
        const val DE_VERSION = "1.0"
        const val CHIPER_SUITE_IDENT = 1
        const val COSE_KEY_KTY = "tbd"
    }

    var deviceEngagementQr = ObservableField<Bitmap>()
    var qrcodeVisibility = ObservableInt()
    var permissionRequestVisibility = ObservableInt()
    var permissionRequestText = ObservableField<String>()
    var btnEnableBtVisibility = ObservableInt()
    var btnReqPermissionVisibility = ObservableInt()
    var loadingVisibility = ObservableInt()
    private var offlineTransferStatusLd = MutableLiveData<Resource<Any>>()
    private var iofflineTransferHolder : IofflineTransfer? = null
    private var liveDataMerger = MediatorLiveData<Resource<Any>>()


    fun getOfflineTransferStatusLd() : LiveData<Resource<Any>> {
        return liveDataMerger
    }

    fun setUp() {
        permissionRequestVisibility.set(View.GONE)
        qrcodeVisibility.set(View.GONE)
        btnReqPermissionVisibility.set(View.GONE)
        btnEnableBtVisibility.set(View.GONE)
    }

    fun createDeQrCode(transferMethod: TransferChannels) {
        createQrCodeAsync(transferMethod)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(QrcodeConsumer())
    }

    fun onUserConsent(userConsentMap: Map<String, Boolean>?) {
        viewModelScope.launch {
            iofflineTransferHolder?.onUserConsent(userConsentMap)
        }
    }

    fun onUserConsentCancel() {
        viewModelScope.launch {
            // null as a parameter here means that the user cancelled the consent dialog
            // and will trigger the mDL Holder to send the response as error 19
            iofflineTransferHolder?.onUserConsent(null)
        }
    }

    fun getCryptoObject() : BiometricPrompt.CryptoObject? {
        return iofflineTransferHolder?.getCryptoObject()
    }

    private fun createQrCodeAsync(transferMethod: TransferChannels) : Single<Bitmap> {
        return Single.create { emitter ->
            viewModelScope.launch {
                try {
                    // Session Manager is used to Encrypt/Decrypt Messages
                    val sessionManager = HolderSessionManager.
                        getInstance(app.applicationContext, CREDENTIAL_NAME)

                    // Set up a new holder session so the Device Engagement COSE_Key is ephemeral to this engagement
                    sessionManager.initializeHolderSession()

                    // Check if there are device keys needing certification
                    sessionManager.checkDeviceKeysNeedingCertification(
                        MockIssuerAuthority.getInstance(app.applicationContext))

                    // Generate a CoseKey with an Ephemeral Key
                    val coseKey = sessionManager
                        .generateHolderCoseKey()
                        ?: throw IdentityCredentialException("Error generating Holder CoseKey")

                    val security = Security.Builder()
                        .setCoseKey(coseKey)
                        .setCipherSuiteIdent(CHIPER_SUITE_IDENT)
                        .build()

                    val builder = DeviceEngagement.Builder()

                    builder.version(DE_VERSION)
                    builder.security(security)

                    when (transferMethod) {
                        TransferChannels.BLE -> {
                            val peripheralMode = BleUtils.isPeripheralSupported(app.applicationContext)
                            val centralMode = BleUtils.isCentralModeSupported(app.applicationContext)
                            builder.transferMethods(
                                BleTransferMethod(
                                    DeviceEngagement.TRANSFER_TYPE_BLE, BLE_VERSION,
                                    BleTransferMethod.BleIdentification(
                                        peripheralMode, centralMode, null, null
                                    )
                                )
                            )
                        }
                        TransferChannels.WiFiAware -> {
                            builder.transferMethods(WiFiAwareTransferMethod(DeviceEngagement.TRANSFER_TYPE_WIFI_AWARE, WIFI_AWARE_VERSION))
                        }
                        else -> throw UnsupportedOperationException("Unsupported transfer method requested in QR code")
                    }

                    val de = builder.build()
                    val mdlQrCode = MdlQrCode.Builder()
                        .setDeviceEngagement(de)
                        .build()

                    setupHolder(de)
                    mdlQrCode.getQrCode()?.let {
                        emitter.onSuccess(it)
                    }

                } catch (ex: IdentityCredentialException) {
                    Log.e(LOG_TAG, ex.message, ex)

                    emitter.onError(ex)
                }
            }
        }
    }

    private inner class QrcodeConsumer : SingleObserver<Bitmap> {
        override fun onSuccess(t: Bitmap) {
            deviceEngagementQr.set(t)
            qrcodeVisibility.set(View.VISIBLE)
            loadingVisibility.set(View.GONE)
        }

        override fun onSubscribe(d: Disposable) {
            qrcodeVisibility.set(View.GONE)
            loadingVisibility.set(View.VISIBLE)
        }

        override fun onError(e: Throwable) {
            qrcodeVisibility.set(View.GONE)
            loadingVisibility.set(View.GONE)
        }
    }

    fun isBleEnabled(isEnabled: Boolean) {
        if (isEnabled) {
            permissionRequestVisibility.set(View.GONE)
            btnEnableBtVisibility.set(View.GONE)
        } else {
            permissionRequestText.set(app.applicationContext.getString(R.string.ble_disabled_txt))
            permissionRequestVisibility.set(View.VISIBLE)
            btnEnableBtVisibility.set(View.VISIBLE)
        }
    }

    fun isPermissionGranted(isGranted: Boolean) {
        if (isGranted) {
            permissionRequestVisibility.set(View.GONE)
            btnReqPermissionVisibility.set(View.GONE)
        } else {
            permissionRequestText.set(app.applicationContext.getString(R.string.location_permission_txt))
            permissionRequestVisibility.set(View.VISIBLE)
            btnReqPermissionVisibility.set(View.VISIBLE)
        }
    }

    private fun setupHolder(deviceEngagement: DeviceEngagement) {
        doAsync {
            val coseKey = deviceEngagement.security?.coseKey

            coseKey?.let {cKey ->
                val builder = OfflineTransferManager.Builder()
                    .actAs(AppMode.HOLDER)
                    .setContext(app.applicationContext)
                    .setDataType(DataTypes.CBOR)
                    .setCoseKey(cKey)

                val bleTransportMethod = deviceEngagement.getBLETransferMethod()
                val wifiTransportMethod = deviceEngagement.getWiFiAwareTransferMethod()

                bleTransportMethod?.let { bleTransport ->

                    builder.setTransferChannel(TransferChannels.BLE)

                    // both modes are supported
                    if (bleTransport.bleIdentification?.centralClient == true &&
                        bleTransport.bleIdentification?.peripheralServer == true) {
                        // When the mDL supports both modes, the mDL reader should act as BLE central mode.
                        builder.setBleServiceMode(BleServiceMode.PERIPHERAL_SERVER_MODE)
                    } else {
                        // only central client mode supported
                        if (bleTransport.bleIdentification?.centralClient == true) {
                            builder.setBleServiceMode(BleServiceMode.CENTRAL_CLIENT_MODE)
                        } else {
                            // only peripheral server mode supported
                            builder.setBleServiceMode(BleServiceMode.PERIPHERAL_SERVER_MODE)
                        }
                    }
                }

                wifiTransportMethod?.let {
                    builder.setTransferChannel(TransferChannels.WiFiAware)
                }

                iofflineTransferHolder = builder.build()
                iofflineTransferHolder?.let { holder ->
                    // Set Data to be sent to the Verifier over WiFi
                    val issuerAuthority = MockIssuerAuthority.getInstance(app.applicationContext)

                    val icAPI = ProvisioningManager.getIdentityCredential(app.applicationContext, CREDENTIAL_NAME)

                    icAPI?.let {ic ->
                            holder.setupHolder(CREDENTIAL_NAME, deviceEngagement.encode(), ic,
                                SharedPreferenceUtils(app.applicationContext).isBiometricAuthRequired(),
                                issuerAuthority)
                    }
                    uiThread {
                        iofflineTransferHolder?.data?.let {livedata ->
                            liveDataMerger.addSource(livedata) {
                                liveDataMerger.value = it
                            }
                        }
                    }
                }
            } ?: kotlin.run {
                Log.e(LOG_TAG, "CoseKey in the Device Engagement is null")
            }
        }
    }

    fun tearDownTransfer() {
        iofflineTransferHolder?.tearDown()
        liveDataMerger.removeSource(offlineTransferStatusLd)
    }
}
