package com.ul.ims.gmdl.nfcofflinetransfer.picc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.util.Log
import com.ul.ims.gmdl.nfcengagement.NfcHandler
import com.ul.ims.gmdl.nfcengagement.toHexString
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer

class NfcPicc(
    private val context: Context
) : ITransportLayer {
    private var eventListener : IExecutorEventListener? = null

    companion object {
        private val TAG = "NfcPicc"
        private val PICC_ACTION = "com.ul.ims.gmdl.NfcHandler"
    }

    override fun setEventListener(eventListener: IExecutorEventListener?) {
        Log.d(TAG, "in setEventListener")
        this.eventListener = eventListener
    }

    override fun closeConnection() {
        Log.d(TAG, "in closeConnection")

        val intent = Intent(context, NfcHandler::class.java)
        context.stopService(intent)
    }

    override fun inititalize(publicKeyHash: ByteArray?) {
        Log.d(TAG, "in inititalize")

        val intent = Intent(context, NfcHandler::class.java)
        context.startService(intent)
        context.registerReceiver(PiccBroadcastReceiver(), IntentFilter(PICC_ACTION))
    }

    override fun write(data: ByteArray?) {
        Log.d(TAG, "in write data " + toHexString(data))

    }

    override fun close() {
        Log.d(TAG, "in close")

    }

    private inner class PiccBroadcastReceiver() : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if(it.action.equals(PICC_ACTION)) {
                    var data = it.getByteArrayExtra("data")
                    Log.d(TAG, "calling eventListener's onReceive" + toHexString(data))
                    eventListener?.onReceive(data)
                }
            }
        }
    }
}