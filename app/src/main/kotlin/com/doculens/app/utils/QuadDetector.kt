package com.doculens.app.utils

import android.graphics.Bitmap
import com.doculens.app.domain.DocumentQuad
import com.doculens.app.domain.QuadPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object QuadDetector {

    fun detect(bitmap: Bitmap): DocumentQuad? {
        if (bitmap.width < 24 || bitmap.height < 24) return null
        val maxSide = 380
        val scale = min(1f, maxSide.toFloat() / max(bitmap.width, bitmap.height).toFloat())
        val w = max(24, (bitmap.width * scale).roundToInt())
        val h = max(24, (bitmap.height * scale).roundToInt())
        val small = try {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } catch (_: Throwable) {
            return null
        }
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        val gray = IntArray(w * h) { i ->
            val c = pixels[i]
            ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }
        return brightRegion(gray, w, h) ?: edgeRegion(gray, w, h)
    }

    private fun brightRegion(gray: IntArray, w: Int, h: Int): DocumentQuad? {
        val threshold = otsu(gray)
        if (threshold < 20 || threshold > 250) return null
        val mask = BooleanArray(gray.size) { gray[it] > threshold }
        val foreground = mask.count { it }
        val ratio = foreground.toFloat() / gray.size.toFloat()
        if (ratio < 0.08f || ratio > 0.97f) return null
        val stats = largestComponent(mask, w, h, eightConnected = false) ?: return null
        if (stats.count < gray.size * 0.08f) return null
        val bboxRatio = (stats.maxX - stats.minX + 1).toFloat() * (stats.maxY - stats.minY + 1).toFloat() /
            (w.toFloat() * h.toFloat())
        if (bboxRatio < 0.12f || bboxRatio > 0.985f) return null
        val fill = stats.count.toFloat() / ((stats.maxX - stats.minX + 1).toFloat() * (stats.maxY - stats.minY + 1).toFloat())
        if (fill < 0.42f) return null
        return buildQuad(stats, w, h)
    }

    private fun edgeRegion(gray: IntArray, w: Int, h: Int): DocumentQuad? {
        val magnitudes = IntArray(w * h)
        val histogram = IntArray(1024)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -gray[i - w - 1] - 2 * gray[i - 1] - gray[i + w - 1] +
                    gray[i - w + 1] + 2 * gray[i + 1] + gray[i + w + 1]
                val gy = -gray[i - w - 1] - 2 * gray[i - w] - gray[i - w + 1] +
                    gray[i + w - 1] + 2 * gray[i + w] + gray[i + w + 1]
                val mag = sqrt((gx * gx + gy * gy).toFloat()).roundToInt().coerceIn(0, 1023)
                magnitudes[i] = mag
                histogram[mag]++
            }
        }
        var cumulative = 0
        val target = (w * h * 0.08f).toInt()
        var threshold = 0
        for (i in histogram.indices) {
            cumulative += histogram[i]
            if (cumulative >= target) {
                threshold = i
                break
            }
        }
        threshold = max(threshold, 60)
        if (threshold >= 900) return null
        val mask = BooleanArray(w * h) { magnitudes[it] >= threshold }
        val stats = largestComponent(mask, w, h, eightConnected = true) ?: return null
        if (stats.count < w * h * 0.012f) return null
        val bboxRatio = (stats.maxX - stats.minX + 1).toFloat() * (stats.maxY - stats.minY + 1).toFloat() /
            (w.toFloat() * h.toFloat())
        if (bboxRatio < 0.16f || bboxRatio > 0.96f) return null
        return buildQuad(stats, w, h)
    }

    private fun otsu(gray: IntArray): Int {
        val histogram = IntArray(256)
        for (v in gray) histogram[v.coerceIn(0, 255)]++
        val total = gray.size
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * histogram[i]
        var sumB = 0.0
        var weightB = 0
        var best = 0.0
        var threshold = 0
        for (t in 0..255) {
            weightB += histogram[t]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += t.toDouble() * histogram[t]
            val meanB = sumB / weightB
            val meanF = (sum - sumB) / weightF
            val between = weightB.toDouble() * weightF * (meanB - meanF) * (meanB - meanF)
            if (between > best) {
                best = between
                threshold = t
            }
        }
        return threshold
    }

    private class Stats(
        val count: Int,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val topLeft: QuadPoint,
        val topRight: QuadPoint,
        val bottomRight: QuadPoint,
        val bottomLeft: QuadPoint
    )

    private fun largestComponent(mask: BooleanArray, w: Int, h: Int, eightConnected: Boolean): Stats? {
        val visited = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        var best: Stats? = null
        var bestCount = 0
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            stack[tail++] = start
            visited[start] = true
            var count = 0
            var minX = w
            var minY = h
            var maxX = 0
            var maxY = 0
            var tl = QuadPoint(1f, 1f)
            var tr = QuadPoint(0f, 0f)
            var br = QuadPoint(0f, 0f)
            var bl = QuadPoint(1f, 1f)
            var tlScore = Int.MAX_VALUE
            var trScore = Int.MIN_VALUE
            var brScore = Int.MIN_VALUE
            var blScore = Int.MAX_VALUE
            while (head < tail) {
                val idx = stack[head++]
                val x = idx % w
                val y = idx / w
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                val sum = x + y
                val diff = x - y
                if (sum < tlScore) { tlScore = sum; tl = QuadPoint(x.toFloat(), y.toFloat()) }
                if (sum > brScore) { brScore = sum; br = QuadPoint(x.toFloat(), y.toFloat()) }
                if (diff > trScore) { trScore = diff; tr = QuadPoint(x.toFloat(), y.toFloat()) }
                if (diff < blScore) { blScore = diff; bl = QuadPoint(x.toFloat(), y.toFloat()) }
                addNeighbor(idx - 1, x > 0, w, h, mask, visited, stack, tail)?.let { tail = it }
                addNeighbor(idx + 1, x < w - 1, w, h, mask, visited, stack, tail)?.let { tail = it }
                addNeighbor(idx - w, y > 0, w, h, mask, visited, stack, tail)?.let { tail = it }
                addNeighbor(idx + w, y < h - 1, w, h, mask, visited, stack, tail)?.let { tail = it }
                if (eightConnected) {
                    addNeighbor(idx - w - 1, x > 0 && y > 0, w, h, mask, visited, stack, tail)?.let { tail = it }
                    addNeighbor(idx - w + 1, x < w - 1 && y > 0, w, h, mask, visited, stack, tail)?.let { tail = it }
                    addNeighbor(idx + w - 1, x > 0 && y < h - 1, w, h, mask, visited, stack, tail)?.let { tail = it }
                    addNeighbor(idx + w + 1, x < w - 1 && y < h - 1, w, h, mask, visited, stack, tail)?.let { tail = it }
                }
            }
            if (count > bestCount) {
                bestCount = count
                best = Stats(
                    count = count,
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    topLeft = QuadPoint(tl.x / w, tl.y / h),
                    topRight = QuadPoint(tr.x / w, tr.y / h),
                    bottomRight = QuadPoint(br.x / w, br.y / h),
                    bottomLeft = QuadPoint(bl.x / w, bl.y / h)
                )
            }
        }
        return best
    }

    private fun addNeighbor(
        idx: Int,
        valid: Boolean,
        w: Int,
        h: Int,
        mask: BooleanArray,
        visited: BooleanArray,
        stack: IntArray,
        tail: Int
    ): Int? {
        if (!valid || idx < 0 || idx >= mask.size) return null
        if (!mask[idx] || visited[idx]) return null
        visited[idx] = true
        stack[tail] = idx
        return tail + 1
    }

    private fun buildQuad(stats: Stats, w: Int, h: Int): DocumentQuad? {
        val raw = listOf(stats.topLeft, stats.topRight, stats.bottomRight, stats.bottomLeft)
        val ordered = orderQuad(raw) ?: return null
        val minDistance = 0.04f
        for (i in 0 until 4) {
            val a = ordered[i]
            val b = ordered[(i + 1) % 4]
            if (abs(a.x - b.x) < minDistance / 2f && abs(a.y - b.y) < minDistance / 2f) return null
        }
        val area = abs(polygonArea(ordered))
        if (area < 0.08f || area > 0.995f) return null
        if (!isConvex(ordered)) return null
        val inset = 0.002f
        return DocumentQuad(
            topLeft = QuadPoint(ordered[0].x.coerceIn(inset, 1f - inset), ordered[0].y.coerceIn(inset, 1f - inset)),
            topRight = QuadPoint(ordered[1].x.coerceIn(inset, 1f - inset), ordered[1].y.coerceIn(inset, 1f - inset)),
            bottomRight = QuadPoint(ordered[2].x.coerceIn(inset, 1f - inset), ordered[2].y.coerceIn(inset, 1f - inset)),
            bottomLeft = QuadPoint(ordered[3].x.coerceIn(inset, 1f - inset), ordered[3].y.coerceIn(inset, 1f - inset))
        )
    }

    private fun orderQuad(points: List<QuadPoint>): List<QuadPoint>? {
        if (points.size != 4) return null
        val unique = points.distinct()
        if (unique.size < 4) return null
        val cx = unique.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = unique.sumOf { it.y.toDouble() }.toFloat() / 4f
        val sorted = unique.sortedBy { atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        var startIndex = 0
        var best = Float.MAX_VALUE
        sorted.forEachIndexed { index, p ->
            val score = p.x + p.y
            if (score < best) {
                best = score
                startIndex = index
            }
        }
        val rotated = List(4) { sorted[(startIndex + it) % 4] }
        return if (polygonArea(rotated) > 0f) rotated else rotated.reversed()
    }

    private fun polygonArea(points: List<QuadPoint>): Float {
        var area = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            area += a.x * b.y - b.x * a.y
        }
        return area / 2f
    }

    private fun isConvex(points: List<QuadPoint>): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-5f) return false
            val current = if (cross > 0) 1 else -1
            if (sign == 0) sign = current else if (sign != current) return false
        }
        return true
    }

    fun orderPoints(points: List<QuadPoint>): DocumentQuad {
        val ordered = orderQuad(points) ?: listOf(
            QuadPoint(0f, 0f),
            QuadPoint(1f, 0f),
            QuadPoint(1f, 1f),
            QuadPoint(0f, 1f)
        )
        return DocumentQuad(ordered[0], ordered[1], ordered[2], ordered[3])
    }
}
