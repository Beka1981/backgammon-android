import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import java.io.File
import java.io.RandomAccessFile

data class NativeSegment(val type: Int, val offset: Long, val address: Long, val size: Long, val alignment: Long)

fun inspectElf(bytes: ByteArray): Pair<Boolean, String> {
    require(bytes.size > 64 && bytes[0] == 0x7f.toByte() && bytes[1] == 69.toByte() && bytes[2] == 76.toByte())
    val elf64 = bytes[4].toInt() == 2
    val b = ByteBuffer.wrap(bytes).order(if (bytes[5].toInt() == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
    if (!elf64) return true to "32-bit ELF: 16 KB 64-bit requirement not applicable"
    val start = b.getLong(32).toInt()
    val stride = b.getShort(54).toInt() and 0xffff
    val count = b.getShort(56).toInt() and 0xffff
    val segments = (0 until count).map {
        val p = start + it * stride
        NativeSegment(b.getInt(p), b.getLong(p + 8), b.getLong(p + 16), b.getLong(p + 40), b.getLong(p + 48))
    }
    val loads = segments.filter { it.type == 1 }
    val relro = segments.filter { it.type == 0x6474e552 }
    val loadOk = loads.isNotEmpty() && loads.all { it.alignment >= 16384 &&
        it.offset % 16384 == it.address % 16384 }
    val relroOk = relro.all { r ->
        val end = r.address + r.size
        end % 16384 == 0L || loads.any { l ->
            r.address >= l.address && end == l.address + l.size
        }
    }
    return (loadOk && relroOk) to
        ("LOAD alignment=" + loads.joinToString { "0x" + it.alignment.toString(16) } +
        "; RELRO=" + relro.joinToString { r ->
            val end = r.address + r.size
            "end=0x" + end.toString(16) + " suffix=" + loads.any { end == it.address + it.size }
        } + "; LOAD_OK=" + loadOk + "; RELRO_OK=" + relroOk)
}

/** Read ZIP central-directory local offsets, then verify stored native data offsets. */
fun inspectZip(file: File): List<Pair<Boolean, String>> = RandomAccessFile(file, "r").use { input ->
    val tailSize = minOf(input.length(), 65557L).toInt()
    val tail = ByteArray(tailSize)
    input.seek(input.length() - tailSize); input.readFully(tail)
    val t = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
    val eocd = (tailSize - 22 downTo 0).first { t.getInt(it) == 0x06054b50 }
    val directory = t.getInt(eocd + 16).toLong() and 0xffffffffL
    val count = t.getShort(eocd + 10).toInt() and 0xffff
    var position = directory
    val results = mutableListOf<Pair<Boolean, String>>()
    repeat(count) {
        val header = ByteArray(46)
        input.seek(position); input.readFully(header)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        require(b.getInt(0) == 0x02014b50)
        val method = b.getShort(10).toInt() and 0xffff
        val nameSize = b.getShort(28).toInt() and 0xffff
        val extraSize = b.getShort(30).toInt() and 0xffff
        val commentSize = b.getShort(32).toInt() and 0xffff
        val local = b.getInt(42).toLong() and 0xffffffffL
        val nameBytes = ByteArray(nameSize); input.readFully(nameBytes)
        val name = nameBytes.toString(Charsets.UTF_8)
        if (name.startsWith("lib/") && name.endsWith(".so")) {
            val localHeader = ByteArray(30)
            input.seek(local); input.readFully(localHeader)
            val lb = ByteBuffer.wrap(localHeader).order(ByteOrder.LITTLE_ENDIAN)
            val data = local + 30 + (lb.getShort(26).toInt() and 0xffff) + (lb.getShort(28).toInt() and 0xffff)
            val ok = method != 0 || data % 16384 == 0L
            results += ok to (name + " method=" + method + " dataOffset=" + data + " ZIP_16K_OK=" + ok)
        }
        position += 46 + nameSize + extraSize + commentSize
    }
    results
}

gradle.projectsEvaluated {
    val app = rootProject.project(":app")
    app.tasks.register("auditNativeAlignment") {
        doLast {
            val variant = rootProject.findProperty("nativeAuditVariant")?.toString() ?: "debug"
            val output = rootProject.file("app/build/reports/task05/native-" + variant + ".txt")
            val lines = mutableListOf<String>()
            var errors = 0
            val configuration = app.configurations.getByName(variant + "RuntimeClasspath")
            configuration.resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }.forEach { artifact ->
                if (artifact.file.extension == "aar") ZipFile(artifact.file).use { zip ->
                    zip.entries().asSequence().filter { it.name.startsWith("jni/") && it.name.endsWith(".so") }.forEach { entry ->
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val (ok, details) = inspectElf(bytes)
                        lines += artifact.moduleVersion.id.toString() + " " + entry.name + ": " + details
                        if (!ok) errors++
                        val extracted = File(output.parentFile, "native-libs/" + artifact.name + "/" + entry.name)
                        extracted.parentFile.mkdirs(); extracted.writeBytes(bytes)
                    }
                }
            }
            val apk = rootProject.file(rootProject.findProperty("nativeAuditApk")?.toString()
                ?: ("app/build/outputs/apk/" + variant + "/app-" + variant +
                    (if (variant == "release") "-unsigned.apk" else ".apk")))
            require(apk.isFile) { "Build APK first: " + apk }
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }.forEach { entry ->
                    val (ok, details) = inspectElf(zip.getInputStream(entry).use { it.readBytes() })
                    lines += "APK " + entry.name + ": " + details
                    if (!ok) errors++
                }
            }
            inspectZip(apk).forEach { (ok, detail) -> lines += detail; if (!ok) errors++ }
            lines += "TOTAL_ALIGNMENT_ERRORS=" + errors
            output.parentFile.mkdirs(); output.writeText(lines.joinToString("\n") + "\n")
            logger.lifecycle(lines.joinToString("\n"))
            check(errors == 0) { "Native alignment failed; report: " + output }
        }
    }
}
