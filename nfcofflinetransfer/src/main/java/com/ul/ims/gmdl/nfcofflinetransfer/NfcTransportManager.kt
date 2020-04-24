/*
 * Copyright (C) 2020 Google LLC
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

package com.ul.ims.gmdl.nfcofflinetransfer

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ul.ims.gmdl.nfcofflinetransfer.pcd.NfcPcd
import com.ul.ims.gmdl.nfcofflinetransfer.picc.NfcPicc
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransportManager

class NfcTransportManager(
    private val context: Context,
    private val appMode: AppMode,
    private val nfcTag: IsoDep?
) : TransportManager {
    private var transportEventListener: ITransportEventListener? = null
    private var transportLayer: ITransportLayer? = null

    companion object {
        private val TAG = NfcTransportManager.javaClass.simpleName
    }

    override fun setTransportProgressListener(transportEventListener: ITransportEventListener) {
        this.transportEventListener = transportEventListener
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun getTransportLayer(): ITransportLayer {
        var nfcAdapter = NfcAdapter.getDefaultAdapter(context)

        Log.d(TAG, "Setup NfcTransportManager for $appMode")
        transportLayer = when (appMode) {
            AppMode.HOLDER -> {
                nfcAdapter?.let {
                    NfcPicc(context)
                }
            }
            AppMode.VERIFIER -> {
                nfcTag?.let {
                    NfcPcd(nfcTag)
                }
            }
        }
        return transportLayer?: throw UnsupportedOperationException("Nfc service is null")
    }

    override fun setReadyForNextFile(boolean: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}