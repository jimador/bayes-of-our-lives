package com.embabel.tripper.agent

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.*

/** One user-triggered OSM lookup, bounded to three real museum leads. */
@Component
class CityOutingProvider(@Value("\${tripper.outing.nominatim-base-url:https://nominatim.openstreetmap.org}") base: String) {
    data class Place(val name: String, val latitude: Double, val longitude: Double, val sourceUrl: String)
    data class Option(val place: Place, val temperatureC: Double?, val rainProbability: Double?,
                      val windKt: Double?, val forecastHourUtc: Instant?, val weatherUrl: String,
                      val baseDriveMinutes: Int?)
    data class Search(val city: String, val startUtc: Instant, val retrievedUtc: Instant,
                      val geocodingUrl: String, val options: List<Option>)
    private val nominatimBaseUrl = base.trimEnd('/')
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val mapper = ObjectMapper()
    private val agent = "TripperCityOuting/0.1 (github.com/embabel/tripper)"

    fun search(destination: String, origin: String?, requestedStart: Instant?): Search {
        require(destination.isNotBlank()) { "A destination is required" }
        val geoUrl = geocodeUrl(destination)
        val city = geocode(destination)
        val start = requestedStart ?: run {
            val zone = ZoneId.of(city.path("timezone").asText("UTC"))
            var local = ZonedDateTime.now(zone).withHour(14).withMinute(0).withSecond(0).withNano(0)
            if (!local.toInstant().isAfter(Instant.now())) local = local.plusDays(1)
            local.toInstant()
        }
        require(start.isAfter(Instant.now()) && start.isBefore(Instant.now().plusSeconds(6 * 86400))) {
            "Outing hour must be within the next six days"
        }
        val from = if (origin.isNullOrBlank() || origin.equals(destination, ignoreCase = true)) city else geocode(origin)
        val lat = city.path("latitude").asDouble()
        val lon = city.path("longitude").asDouble()
        val searchUrl = "$nominatimBaseUrl/search?q=${enc("museum, $destination")}&format=jsonv2&limit=3"
        val points = get(searchUrl)
        val options = mutableListOf<Option>()
        points.forEach { point ->
            if (!point.hasNonNull("lat") || !point.hasNonNull("lon")) return@forEach
            val pLat = point.path("lat").asDouble()
            val pLon = point.path("lon").asDouble()
            if (distanceKm(lat, lon, pLat, pLon) > 25) return@forEach
            val type = point.path("osm_type").asText()
            if (type !in setOf("node", "way", "relation")) return@forEach
            val id = point.path("osm_id").asText()
            val name = point.path("name").asText("").ifBlank {
                point.path("display_name").asText("Mapped museum").substringBefore(',')
            }
            val place = Place(name, pLat, pLon, "https://www.openstreetmap.org/$type/$id")
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$pLat&longitude=$pLon" +
                "&timezone=UTC&forecast_days=7&hourly=temperature_2m,precipitation_probability,wind_speed_10m&wind_speed_unit=kn"
            val hourly = get(weatherUrl).path("hourly")
            val index = nearest(hourly.path("time"), start)
            val baseDrive = try {
                val routeUrl = "http://router.project-osrm.org/route/v1/driving/" +
                    "${from.path("longitude").asDouble()},${from.path("latitude").asDouble()};$pLon,$pLat?overview=false"
                val route = get(routeUrl)
                val seconds = route.path("routes").path(0).path("duration")
                if (route.path("code").asText() == "Ok" && seconds.isNumber) ceil(seconds.asDouble() / 60).toInt() else null
            } catch (_: Exception) { null }
            options += Option(place, number(hourly.path("temperature_2m"), index),
                number(hourly.path("precipitation_probability"), index),
                number(hourly.path("wind_speed_10m"), index),
                if (index < 0) null else LocalDateTime.parse(hourly.path("time").get(index).asText()).toInstant(ZoneOffset.UTC),
                weatherUrl, baseDrive)
        }
        val label = city.path("name").asText() + ", " + city.path("country").asText()
        return Search(label, start, Instant.now(), geoUrl, options.toList())
    }

    private fun geocode(text: String): JsonNode {
        val found = get(geocodeUrl(text)).path("results")
        require(found.isArray && !found.isEmpty) { "Destination was not geocoded: $text" }
        return found.get(0)
    }
    private fun geocodeUrl(text: String) = "https://geocoding-api.open-meteo.com/v1/search?name=${enc(text)}&count=1&language=en&format=json"
    private fun get(url: String): JsonNode {
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
            .header("User-Agent", agent).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) { "Public provider ${URI.create(url).host} HTTP ${response.statusCode()}" }
        return mapper.readTree(response.body())
    }
    private fun nearest(times: JsonNode, at: Instant): Int {
        if (!times.isArray) return -1
        var best = Long.MAX_VALUE
        var selected = -1
        times.forEachIndexed { index, node ->
            val time = LocalDateTime.parse(node.asText()).toInstant(ZoneOffset.UTC)
            val seconds = abs(Duration.between(time, at).seconds)
            if (seconds < best) { best = seconds; selected = index }
        }
        return if (best <= 3600) selected else -1
    }
    private fun number(array: JsonNode, index: Int): Double? =
        if (index < 0 || !array.isArray || index >= array.size() || !array.get(index).isNumber) null
        else array.get(index).asDouble()
    private fun distanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val p1 = Math.toRadians(aLat); val p2 = Math.toRadians(bLat)
        val dp = p2 - p1; val dl = Math.toRadians(bLon - aLon)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 6371 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
