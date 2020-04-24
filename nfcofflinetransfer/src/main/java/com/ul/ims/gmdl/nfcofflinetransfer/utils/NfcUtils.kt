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

package com.ul.ims.gmdl.nfcofflinetransfer.utils

import android.content.Context
import android.nfc.NfcAdapter

object NfcUtils {

    /**
     * Return true if NFC is currently enabled and ready for use
     * **/
    fun isNfcEnabled(context : Context) : Boolean {
        return getNfcAdapter(context)?.isEnabled ?: false
    }

    /**
     * Get the default NFC Adapter for this device
     * **/
    fun getNfcAdapter(context : Context) : NfcAdapter? {
        return NfcAdapter.getDefaultAdapter(context)
    }
}