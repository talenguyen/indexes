package vn.tale.stockupdate

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

data class Index(val code: String, val price: Double, val ceilPrice: Double, val floorPrice: Double)

val client = OkHttpClient()
val vnIndexUrl = "http://priceboard.fpts.com.vn/hsx/data.ashx?s=quote&l=All"
val hnxIndexUrl = "http://priceboard.fpts.com.vn/hnx/data.ashx?s=quote&l=HNXIndex"
val upcomIndexUrl = "http://priceboard.fpts.com.vn/hnx/data.ashx?s=quote&l=HNXUpcomIndex"

fun getIndexes(url: String): List<Index> {
  val request = Request.Builder()
    .url(url)
    .build()

  val response = client.newCall(request).execute()
  if (response.isSuccessful) {
    val json = response.body()?.string()
    return JSONArray(json).asSequence()
      .map {
        val infos = it.getJSONArray("Info")
          .asArraySequence()
          .sortedBy { it[0].toString().toInt() }
          .take(4)
          .map { it[1] }
          .toList()
        val code = infos[0].toString()
        val price = infos[1].toString().toDouble()
        val ceilPrice = infos[2].toString().toDouble()
        val floorPrice = infos[3].toString().toDouble()
        Index(code, price, ceilPrice, floorPrice)
      }
      .toList()
  } else {
    throw Exception("error: ${response.code()}, message: ${response.message()}")
  }
}

fun Index.toCsvRow() = "$code, $price, $ceilPrice, $floorPrice"

fun <T> (() -> T).retry(time: Int): T? {
  var count = 1
  while (count < time) {
    try {
      return invoke()
    } catch (e: Exception) {
      e.printStackTrace()
      count++
    }
  }
  return null
}

fun currentDir(): File = Paths.get(".").toAbsolutePath().toFile()

fun main(args: Array<String>) {
  val startAt = System.currentTimeMillis()
  val vnIndexes = { getIndexes(vnIndexUrl) }.retry(3) ?: exitProcess(1)
  val hnxIndexes = { getIndexes(hnxIndexUrl) }.retry(3) ?: exitProcess(1)
  val upcomIndexes = { getIndexes(upcomIndexUrl) }.retry(3) ?: exitProcess(1)

  val csvContent = mutableListOf<Index>()
    .apply {
      addAll(vnIndexes)
      addAll(hnxIndexes)
      addAll(upcomIndexes)
    }
    .distinctBy { it.code }
    .joinToString("\n") { it.toCsvRow() }

  File(currentDir(), "indexes.csv")
    .writeText(csvContent)

  val duration = System.currentTimeMillis() - startAt

  println("Success took ${duration / 1000} secs")
  exitProcess(0)
}