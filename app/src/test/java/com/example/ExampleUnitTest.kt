package com.example

import com.example.data.model.CadastreLot
import com.example.data.repository.GeocodingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testCadastreLotAddressFallback() {
        val lotWithUsual = CadastreLot(
            objectId = 101L,
            endUsual = "SHCN SQN 205 BL G",
            endCartorial = "SHCN SQN 205 PJ 7"
        )
        assertEquals("SHCN SQN 205 BL G", lotWithUsual.displayAddress)

        val lotWithoutUsual = CadastreLot(
            objectId = 102L,
            endUsual = "",
            endCartorial = "SHCN SQN 205 PJ 7"
        )
        assertEquals("SHCN SQN 205 PJ 7", lotWithoutUsual.displayAddress)
    }

    @Test
    fun testCepFormatting() {
        val lot = CadastreLot(cep = "70843070")
        assertEquals("70843-070", lot.formattedCep)
    }

    @Test
    fun testDmsCoordinates() {
        val lot = CadastreLot(latitude = -15.77165, longitude = -47.87985)
        assertTrue(lot.formattedDms.contains("15°"))
        assertTrue(lot.formattedDms.contains("S"))
        assertTrue(lot.formattedDms.contains("47°"))
        assertTrue(lot.formattedDms.contains("W"))
    }

    @Test
    fun testWmsMapUrlBuilder() {
        val repo = GeocodingRepository()
        val url = repo.buildWmsGetMapUrl(
            minLon = -47.88,
            minLat = -15.78,
            maxLon = -47.87,
            maxLat = -15.77,
            width = 512,
            height = 512,
            layers = listOf("5", "2", "1")
        )
        assertTrue(url.contains("SERVICE=WMS"))
        assertTrue(url.contains("REQUEST=GetMap"))
        assertTrue(url.contains("SRS=EPSG:4326"))
        assertTrue(url.contains("LAYERS=5,2,1"))
    }
}
