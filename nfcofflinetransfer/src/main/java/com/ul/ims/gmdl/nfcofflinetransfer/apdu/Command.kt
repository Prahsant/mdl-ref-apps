package com.ul.ims.gmdl.nfcofflinetransfer.apdu

class Command private constructor(private val cla: Byte,
                private val ins: Byte,
                private val p1: Byte,
               private val p2:Byte) {

    private var data: ByteArray? = null
    private var command: MutableList<Byte> = mutableListOf(cla, ins, p1, p2)

    fun addData(data: ByteArray) {
        if(data.size < 256) {
            command.plus(data.size.toByte())
        } else {

        }
    }
}