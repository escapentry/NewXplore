package com.example.files

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Pure-Kotlin decoder for Android Binary XML formats:
 * 1) Compiled AXML (`RES_XML_TYPE` = 0x0003) used in APK manifests, layouts, and compiled resources.
 * 2) Android Binary XML (`ABX\0`) used in Android 12+ `/data/system/` and shared preference XMLs.
 */
object BinaryXmlParser {

  private const val RES_STRING_POOL_TYPE = 0x0001
  private const val RES_XML_TYPE = 0x0003
  private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
  private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
  private const val RES_XML_START_ELEMENT_TYPE = 0x0102
  private const val RES_XML_END_ELEMENT_TYPE = 0x0103
  private const val RES_XML_CDATA_TYPE = 0x0104
  private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180

  private const val UTF8_FLAG = 1 shl 8

  private const val TYPE_NULL = 0x00
  private const val TYPE_REFERENCE = 0x01
  private const val TYPE_ATTRIBUTE = 0x02
  private const val TYPE_STRING = 0x03
  private const val TYPE_FLOAT = 0x04
  private const val TYPE_DIMENSION = 0x05
  private const val TYPE_FRACTION = 0x06
  private const val TYPE_INT_DEC = 0x10
  private const val TYPE_INT_HEX = 0x11
  private const val TYPE_INT_BOOLEAN = 0x12
  private const val TYPE_INT_COLOR_ARGB8 = 0x1c
  private const val TYPE_INT_COLOR_RGB8 = 0x1d
  private const val TYPE_INT_COLOR_ARGB4 = 0x1e
  private const val TYPE_INT_COLOR_RGB4 = 0x1f

  private val ABX_MAGIC = byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 'X'.code.toByte(), 0)

  fun isAxml(bytes: ByteArray): Boolean {
    if (bytes.size < 8) return false
    val buf = ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
    val type = buf.short.toInt() and 0xFFFF
    val headerSize = buf.short.toInt() and 0xFFFF
    return type == RES_XML_TYPE && headerSize >= 8
  }

  fun isAbx(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    return bytes[0] == ABX_MAGIC[0] &&
      bytes[1] == ABX_MAGIC[1] &&
      bytes[2] == ABX_MAGIC[2] &&
      bytes[3] == ABX_MAGIC[3]
  }

  fun isBinaryXml(bytes: ByteArray): Boolean = isAxml(bytes) || isAbx(bytes)

  fun decodeToXmlString(bytes: ByteArray): Result<String> = runCatching {
    when {
      isAxml(bytes) -> decodeAxml(bytes)
      isAbx(bytes) -> decodeAbx(bytes)
      else -> bytes.toString(Charsets.UTF_8)
    }
  }

  private fun decodeAxml(bytes: ByteArray): String {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val rootType = buf.short.toInt() and 0xFFFF
    val rootHeaderSize = buf.short.toInt() and 0xFFFF
    val rootChunkSize = buf.int
    require(rootType == RES_XML_TYPE && rootHeaderSize >= 8) { "Not an AXML file" }

    var offset = rootHeaderSize
    val limit = minOf(bytes.size, rootChunkSize)
    var strings: List<String> = emptyList()
    val nsStack = mutableListOf<Pair<String, String>>()
    val pendingNsDecls = mutableListOf<Pair<String, String>>()
    val out = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    var indent = 0
    var openTagHasChildren = false
    var insideStartTag = false

    fun flushOpenTag(selfClosing: Boolean) {
      if (!insideStartTag) return
      if (selfClosing) {
        out.append(" />\n")
      } else {
        out.append(">\n")
      }
      insideStartTag = false
    }

    while (offset + 8 <= limit) {
      buf.position(offset)
      val chunkType = buf.short.toInt() and 0xFFFF
      val headerSize = buf.short.toInt() and 0xFFFF
      val chunkSize = buf.int
      if (chunkSize < 8 || offset + chunkSize > bytes.size) break

      when (chunkType) {
        RES_STRING_POOL_TYPE -> {
          strings = parseStringPool(bytes, offset, chunkSize)
        }

        RES_XML_RESOURCE_MAP_TYPE -> {
          // Resource IDs table (unused directly when string pool holds names)
        }

        RES_XML_START_NAMESPACE_TYPE -> {
          if (offset + 24 <= bytes.size) {
            buf.position(offset + 16)
            val prefixIdx = buf.int
            val uriIdx = buf.int
            val prefix = strings.getOrNull(prefixIdx).orEmpty()
            val uri = strings.getOrNull(uriIdx).orEmpty()
            if (uri.isNotEmpty()) {
              nsStack.add(prefix to uri)
              pendingNsDecls.add(prefix to uri)
            }
          }
        }

        RES_XML_END_NAMESPACE_TYPE -> {
          if (offset + 24 <= bytes.size) {
            buf.position(offset + 16)
            val prefixIdx = buf.int
            val uriIdx = buf.int
            val prefix = strings.getOrNull(prefixIdx).orEmpty()
            val uri = strings.getOrNull(uriIdx).orEmpty()
            val idx = nsStack.indexOfLast { it.first == prefix && it.second == uri }
            if (idx >= 0) nsStack.removeAt(idx)
          }
        }

        RES_XML_START_ELEMENT_TYPE -> {
          flushOpenTag(selfClosing = false)
          openTagHasChildren = true

          buf.position(offset + 16)
          val nsIdx = buf.int
          val nameIdx = buf.int
          val attrStart = buf.short.toInt() and 0xFFFF
          val attrSize = (buf.short.toInt() and 0xFFFF).coerceAtLeast(20)
          val attrCount = buf.short.toInt() and 0xFFFF

          val elemName = qualifyName(
            nsUri = strings.getOrNull(nsIdx),
            name = strings.getOrNull(nameIdx).orEmpty().ifEmpty { "node" },
            nsStack = nsStack,
          )

          out.append("  ".repeat(indent)).append('<').append(elemName)

          if (pendingNsDecls.isNotEmpty()) {
            for ((p, u) in pendingNsDecls) {
              val attrName = if (p.isEmpty()) "xmlns" else "xmlns:$p"
              out.append(' ').append(attrName).append("=\"").append(escapeXml(u)).append('"')
            }
            pendingNsDecls.clear()
          }

          val attrBase = offset + headerSize + (attrStart - 20).coerceAtLeast(0)
          for (i in 0 until attrCount) {
            val aOff = attrBase + i * attrSize
            if (aOff + 20 > offset + chunkSize) break
            buf.position(aOff)
            val aNsIdx = buf.int
            val aNameIdx = buf.int
            val aRawValIdx = buf.int
            buf.short // size
            buf.get() // res0
            val dataType = buf.get().toInt() and 0xFF
            val data = buf.int

            val aName = qualifyName(
              nsUri = strings.getOrNull(aNsIdx),
              name = strings.getOrNull(aNameIdx).orEmpty().ifEmpty { "attr_$i" },
              nsStack = nsStack,
            )
            val aValue = formatTypedValue(dataType, data, aRawValIdx, strings)
            out.append(' ').append(aName).append("=\"").append(escapeXml(aValue)).append('"')
          }

          insideStartTag = true
          openTagHasChildren = false
          indent++
        }

        RES_XML_END_ELEMENT_TYPE -> {
          indent = (indent - 1).coerceAtLeast(0)
          buf.position(offset + 16)
          val nsIdx = buf.int
          val nameIdx = buf.int
          if (insideStartTag && !openTagHasChildren) {
            flushOpenTag(selfClosing = true)
          } else {
            flushOpenTag(selfClosing = false)
            val elemName = qualifyName(
              nsUri = strings.getOrNull(nsIdx),
              name = strings.getOrNull(nameIdx).orEmpty().ifEmpty { "node" },
              nsStack = nsStack,
            )
            out.append("  ".repeat(indent)).append("</").append(elemName).append(">\n")
          }
          openTagHasChildren = true
        }

        RES_XML_CDATA_TYPE -> {
          flushOpenTag(selfClosing = false)
          openTagHasChildren = true
          if (offset + 20 <= bytes.size) {
            buf.position(offset + 16)
            val dataIdx = buf.int
            val text = strings.getOrNull(dataIdx).orEmpty()
            if (text.isNotEmpty()) {
              out.append("  ".repeat(indent)).append(escapeXml(text)).append('\n')
            }
          }
        }
      }

      offset += chunkSize
    }

    flushOpenTag(selfClosing = false)
    return out.toString()
  }

  private fun parseStringPool(bytes: ByteArray, poolOffset: Int, chunkSize: Int): List<String> {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buf.position(poolOffset + 2)
    val headerSize = buf.short.toInt() and 0xFFFF
    buf.position(poolOffset + 8)
    val stringCount = buf.int
    val styleCount = buf.int
    val flags = buf.int
    val stringsStart = buf.int
    buf.int // stylesStart
    val isUtf8 = (flags and UTF8_FLAG) != 0

    val offsetsBase = poolOffset + headerSize
    val dataBase = poolOffset + stringsStart
    val chunkEnd = minOf(bytes.size, poolOffset + chunkSize)
    val list = ArrayList<String>(stringCount.coerceIn(0, 100_000))

    for (i in 0 until stringCount) {
      val idxPos = offsetsBase + i * 4
      if (idxPos + 4 > chunkEnd) break
      buf.position(idxPos)
      val strRel = buf.int
      val pos = dataBase + strRel
      if (pos < 0 || pos >= chunkEnd) {
        list += ""
        continue
      }
      list += if (isUtf8) readUtf8PoolString(bytes, pos, chunkEnd) else readUtf16PoolString(bytes, pos, chunkEnd)
    }
    return list
  }

  private fun readUtf8PoolString(bytes: ByteArray, start: Int, limit: Int): String {
    var p = start
    // Skip UTF-16 length (1 or 2 bytes)
    if (p >= limit) return ""
    if ((bytes[p].toInt() and 0x80) != 0) p += 2 else p += 1
    if (p >= limit) return ""
    // Read UTF-8 byte length (1 or 2 bytes)
    val byteLen = if ((bytes[p].toInt() and 0x80) != 0) {
      if (p + 1 >= limit) return ""
      val hi = bytes[p].toInt() and 0x7F
      val lo = bytes[p + 1].toInt() and 0xFF
      p += 2
      (hi shl 8) or lo
    } else {
      val len = bytes[p].toInt() and 0x7F
      p += 1
      len
    }
    val safeLen = byteLen.coerceIn(0, (limit - p).coerceAtLeast(0))
    return String(bytes, p, safeLen, Charsets.UTF_8)
  }

  private fun readUtf16PoolString(bytes: ByteArray, start: Int, limit: Int): String {
    if (start + 2 > limit) return ""
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buf.position(start)
    var charLen = buf.short.toInt() and 0xFFFF
    var p = start + 2
    if ((charLen and 0x8000) != 0) {
      if (p + 2 > limit) return ""
      val lo = buf.short.toInt() and 0xFFFF
      p += 2
      charLen = ((charLen and 0x7FFF) shl 16) or lo
    }
    val byteLen = (charLen * 2).coerceIn(0, (limit - p).coerceAtLeast(0))
    return String(bytes, p, byteLen, Charsets.UTF_16LE)
  }

  private fun qualifyName(
    nsUri: String?,
    name: String,
    nsStack: List<Pair<String, String>>,
  ): String {
    if (nsUri.isNullOrEmpty()) return name
    val prefix = nsStack.lastOrNull { it.second == nsUri }?.first
    return if (!prefix.isNullOrEmpty()) "$prefix:$name" else name
  }

  private fun formatTypedValue(
    type: Int,
    data: Int,
    rawStrIdx: Int,
    strings: List<String>,
  ): String {
    if (rawStrIdx >= 0) {
      strings.getOrNull(rawStrIdx)?.let { return it }
    }
    return when (type) {
      TYPE_NULL -> ""
      TYPE_STRING -> strings.getOrNull(data).orEmpty()
      TYPE_REFERENCE -> String.format(Locale.US, "@0x%08X", data)
      TYPE_ATTRIBUTE -> String.format(Locale.US, "?0x%08X", data)
      TYPE_FLOAT -> java.lang.Float.intBitsToFloat(data).toString()
      TYPE_INT_DEC -> data.toString()
      TYPE_INT_HEX -> String.format(Locale.US, "0x%x", data)
      TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
      TYPE_DIMENSION -> formatComplex(data, isFraction = false)
      TYPE_FRACTION -> formatComplex(data, isFraction = true)
      TYPE_INT_COLOR_ARGB8, TYPE_INT_COLOR_RGB8, TYPE_INT_COLOR_ARGB4, TYPE_INT_COLOR_RGB4 ->
        String.format(Locale.US, "#%08X", data)
      else -> if (rawStrIdx >= 0) strings.getOrNull(rawStrIdx).orEmpty() else data.toString()
    }
  }

  private fun formatComplex(data: Int, isFraction: Boolean): String {
    val radix = (data shr 4) and 0x3
    val mantissa = data shr 8
    val value = when (radix) {
      0 -> mantissa.toFloat()
      1 -> mantissa / 128f
      2 -> mantissa / 32768f
      else -> mantissa / 8388608f
    }
    val unit = if (isFraction) {
      when (data and 0xF) {
        0 -> "%"
        else -> "%p"
      }
    } else {
      when (data and 0xF) {
        0 -> "px"
        1 -> "dp"
        2 -> "sp"
        3 -> "pt"
        4 -> "in"
        5 -> "mm"
        else -> ""
      }
    }
    val formatted = if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.2f", value)
    return "$formatted$unit"
  }

  /**
   * Decodes Android 12+ ABX binary XML format.
   */
  private fun decodeAbx(bytes: ByteArray): String {
    val input = DataInputStream(ByteArrayInputStream(bytes, 4, bytes.size - 4))
    val interned = mutableListOf<String>()
    val out = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    val tagStack = mutableListOf<String>()
    var openTagPending = false
    var openTagHasChild = false

    fun readInternedString(): String {
      val idx = input.readShort().toInt()
      return if (idx == -1) {
        val len = input.readUnsignedShort()
        val buf = ByteArray(len)
        input.readFully(buf)
        val s = String(buf, Charsets.UTF_8)
        interned += s
        s
      } else {
        interned.getOrElse(idx) { "str_$idx" }
      }
    }

    fun readStringUtf(): String {
      val len = input.readUnsignedShort()
      val buf = ByteArray(len)
      input.readFully(buf)
      return String(buf, Charsets.UTF_8)
    }

    fun closePendingTag(selfClosing: Boolean) {
      if (!openTagPending) return
      out.append(if (selfClosing) " />\n" else ">\n")
      openTagPending = false
    }

    while (input.available() > 0) {
      val packed = input.readUnsignedByte()
      val token = packed and 0x0F
      val type = packed and 0xF0

      when (token) {
        0 /* START_DOCUMENT */, 1 /* END_DOCUMENT */ -> Unit
        2 /* START_TAG */ -> {
          closePendingTag(selfClosing = false)
          openTagHasChild = true
          val name = readInternedString()
          out.append("  ".repeat(tagStack.size)).append('<').append(name)
          tagStack.add(name)
          openTagPending = true
          openTagHasChild = false
        }
        3 /* END_TAG */ -> {
          val expected = readInternedString()
          val name = if (tagStack.isNotEmpty()) tagStack.removeAt(tagStack.lastIndex) else expected
          if (openTagPending && !openTagHasChild) {
            closePendingTag(selfClosing = true)
          } else {
            closePendingTag(selfClosing = false)
            out.append("  ".repeat(tagStack.size)).append("</").append(name).append(">\n")
          }
          openTagHasChild = true
        }
        4 /* TEXT */ -> {
          closePendingTag(selfClosing = false)
          openTagHasChild = true
          val text = readStringUtf()
          if (text.isNotEmpty()) {
            out.append("  ".repeat(tagStack.size)).append(escapeXml(text)).append('\n')
          }
        }
        5 /* CDSECT */ -> {
          closePendingTag(selfClosing = false)
          openTagHasChild = true
          val text = readStringUtf()
          out.append("  ".repeat(tagStack.size)).append("<![CDATA[").append(text).append("]]>\n")
        }
        8 /* COMMENT */ -> {
          closePendingTag(selfClosing = false)
          val text = readStringUtf()
          out.append("  ".repeat(tagStack.size)).append("<!--").append(text).append("-->\n")
        }
        15 /* ATTRIBUTE */ -> {
          val attrName = readInternedString()
          val attrVal = when (type) {
            0x10 /* NULL */ -> "null"
            0x20 /* STRING */ -> readStringUtf()
            0x30 /* STRING_INTERNED */ -> readInternedString()
            0x40 /* BYTES_HEX */ -> {
              val len = input.readUnsignedShort()
              val b = ByteArray(len)
              input.readFully(b)
              b.joinToString("") { "%02x".format(it) }
            }
            0x50 /* BYTES_BASE64 */ -> {
              val len = input.readUnsignedShort()
              val b = ByteArray(len)
              input.readFully(b)
              android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
            }
            0x60 /* INT */ -> input.readInt().toString()
            0x70 /* INT_HEX */ -> "0x" + input.readInt().toUInt().toString(16)
            0x80 /* LONG */ -> input.readLong().toString()
            0x90 /* LONG_HEX */ -> "0x" + input.readLong().toULong().toString(16)
            0xA0 /* FLOAT */ -> input.readFloat().toString()
            0xB0 /* DOUBLE */ -> input.readDouble().toString()
            0xC0 /* BOOLEAN_TRUE */ -> "true"
            0xD0 /* BOOLEAN_FALSE */ -> "false"
            else -> ""
          }
          if (openTagPending) {
            out.append(' ').append(attrName).append("=\"").append(escapeXml(attrVal)).append('"')
          }
        }
        else -> {
          // Consume trailing string payload for other misc tokens if present
          if (type == 0x20 && input.available() >= 2) readStringUtf()
        }
      }
    }

    closePendingTag(selfClosing = false)
    return out.toString()
  }

  private fun escapeXml(raw: String): String = buildString(raw.length) {
    for (ch in raw) {
      when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(ch)
      }
    }
  }
}
