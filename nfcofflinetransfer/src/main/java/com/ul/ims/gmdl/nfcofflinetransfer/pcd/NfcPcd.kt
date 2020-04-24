package com.ul.ims.gmdl.nfcofflinetransfer.pcd

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import androidx.annotation.RequiresApi
import com.ul.ims.gmdl.nfcengagement.toHexString
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.offlinetransfer.utils.Log
import java.io.IOException

@RequiresApi(Build.VERSION_CODES.KITKAT)
class NfcPcd (
    private val nfcTag: IsoDep
): ITransportLayer {
    private var eventListener : IExecutorEventListener? = null

    companion object {
        private val TAG = "NfcPcd"
    }

    override fun setEventListener(eventListener: IExecutorEventListener?) {
        android.util.Log.d(TAG, "in setEventListener")
        this.eventListener = eventListener
    }

    override fun closeConnection() {
        android.util.Log.d(TAG, "in close")
        nfcTag?.close()
    }

    override fun inititalize(publicKeyHash: ByteArray?) {
        android.util.Log.d(TAG, "in inititalize")
        nfcTag?.let { tag ->
            nfcTag?.let {
                try {
                    it.timeout = 5000
                    it.connect()
                    val response = it.transceive(
                        hexStringToByteArray(
                            "00A4040007A0000002480400"
                        )
                    )
                    android.util.Log.d(TAG, "Response from holder $response")

                    if (response.equals(hexStringToByteArray("9000"))) {
                        eventListener?.onEvent(
                            EventType.STATE_READY_FOR_TRANSMISSION.description,
                            EventType.STATE_READY_FOR_TRANSMISSION.ordinal
                        )
                    } else {
                        eventListener?.onEvent(
                            EventType.ERROR.description,
                            EventType.ERROR.ordinal
                        )
                    }
                } catch (eio: IOException) {
                    android.util.Log.e(TAG, "IOException in initialize", eio)
                    eventListener?.onEvent(
                        EventType.ERROR.description,
                        EventType.ERROR.ordinal
                    )
                }
            }
        }
    }

    override fun write(data: ByteArray?) {
        android.util.Log.d(TAG, "in write data " + toHexString(data))

        data?.let {
            nfcTag?.let {
                var apdu = hexStringToByteArray(
                    "00C30000"
                ).toMutableList()
                apdu.add(data.size.toByte())
                apdu.addAll(data.asList())
                var apduHex : StringBuffer = StringBuffer()
                for(b in apdu) {
                    apduHex.append(String.format("%02X ", b))
                }
                android.util.Log.d(TAG, "in write, apdu $apduHex")

                val response = nfcTag?.transceive(
                    apdu.toByteArray()
                )

                android.util.Log.d(TAG, "in write response " + toHexString(response))

                eventListener?.onReceive(response)
            }
        }
    }

    override fun close() {
        android.util.Log.d(TAG, "in close")
    }
/*
    override fun onTagDiscovered(tag: Tag?) {
        Log.d(TAG, "in onTagDiscovered")
        val isoDep = IsoDep.get(tag)
        isoDep.connect()
        val response = isoDep.transceive(hexStringToByteArray(
            "00A4040007A0000002480400"))
        Log.d(TAG, "Response from holder $response")
        isoDep.close()
    }*/

    private val HEX_CHARS = "0123456789ABCDEF"
    fun hexStringToByteArray(data: String) : ByteArray {

        val result = ByteArray(data.length / 2)

        for (i in 0 until data.length step 2) {
            val firstIndex = HEX_CHARS.indexOf(data[i]);
            val secondIndex = HEX_CHARS.indexOf(data[i + 1]);

            val octet = firstIndex.shl(4).or(secondIndex)
            result.set(i.shr(1), octet.toByte())
        }

        return result
    }
}