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

package com.ul.ims.gmdl.offlineTransfer

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.ul.ims.gmdl.bleofflinetransfer.manager.BleTransportManager
import com.ul.ims.gmdl.nfcofflinetransfer.NfcTransportManager
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransportManager
import com.ul.ims.gmdl.offlinetransfer.utils.Log
import com.ul.ims.gmdl.wifiofflinetransfer.WifiTransportManager

class TransportChannelManager(
    context: Context,
    transportChannel: TransferChannels,
    appMode: AppMode,
    bleServiceMode: BleServiceMode,
    publicKey: ByteArray,
    wifiPassphrase: String?,
    nfcTag: IsoDep?
){
    private var transportManager: TransportManager
    init {

        when (transportChannel) {
            TransferChannels.BLE -> {
                Log.d(javaClass.simpleName, "Starting BLE transport manager")
                transportManager = BleTransportManager(
                    context,
                    appMode,
                    bleServiceMode
                )
            }
            TransferChannels.WiFiAware -> {
                Log.d(javaClass.simpleName, "Starting Wifi Aware transport manager")
                transportManager = WifiTransportManager(context, appMode, publicKey, wifiPassphrase)
            }

            TransferChannels.NFC -> {
                Log.d(javaClass.simpleName, "Starting NFC transport manager")
                transportManager = NfcTransportManager(context, appMode, nfcTag)
            }
            else -> throw UnsupportedOperationException("Unknown transport channel: $transportChannel")
        }
    }

    fun getTransportManager() : TransportManager {
        return this.transportManager
    }

    fun getTransportLayer(): ITransportLayer {
        return transportManager.getTransportLayer()
    }
}
