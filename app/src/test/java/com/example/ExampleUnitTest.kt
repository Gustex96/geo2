package com.example

import com.example.data.model.CadastreLot
import com.example.data.model.KmdfRecord
import com.example.data.repository.GeocodingRepository
import com.example.data.repository.KmdfRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun testKmdfPreloadedTable() {
        val kmdfRepo = KmdfRepository()
        val table = kmdfRepo.getPreloadedKmdfTable()
        assertTrue("A tabela KM-DF deve conter registros das rodovias", table.isNotEmpty())

        val df001 = table.find { it.rodovia == "DF-001" }
        assertNotNull("DF-001 deve estar presente", df001)

        val eixao = table.find { it.rodovia == "DF-002" && it.km == 7.0 }
        assertNotNull("KM Zero do Eixão (Rodoviária) deve estar presente", eixao)
        assertTrue(eixao?.trecho?.contains("Plataforma") == true)
    }

    @Test
    fun testKmdfRecordFormatting() {
        val recDecimal = KmdfRecord(
            id = "test_1",
            rodovia = "DF-001",
            km = 10.5,
            trecho = "Trecho Lago Norte",
            ra = "LAGO NORTE",
            latitude = -15.725,
            longitude = -47.854
        )
        assertEquals("DF-001 KM 10.5", recDecimal.displayTitle)
        assertTrue(recDecimal.formattedDecimalCoords.contains("-15.725000"))

        val recInteger = KmdfRecord(
            id = "test_2",
            rodovia = "DF-002",
            km = 7.0,
            trecho = "Rodoviária",
            ra = "PLANO PILOTO",
            latitude = -15.7942,
            longitude = -47.8822
        )
        assertEquals("DF-002 KM 7", recInteger.displayTitle)
    }

    @Test
    fun testResidenceDataFormatting() {
        val residence = CadastreLot(
            objectId = 1L,
            ciu = "0121IV4B0004",
            setor = "SHTN",
            quadra = "TRECHO 1",
            conjunto = "POLO 3",
            lote = "LT 8",
            endUsual = "SHTN TRECHO 1 POLO 3 LT 8",
            ra = "PLANO PILOTO",
            cep = "70800210",
            areaProjeto = 525.0,
            latitude = -15.7863,
            longitude = -47.8335
        )
        assertEquals("SHTN TRECHO 1 POLO 3 LT 8", residence.displayAddress)
        assertEquals("70800-210", residence.formattedCep)
        assertTrue(residence.formattedArea.contains("525"))
        assertEquals("PLANO PILOTO • CEP 70800-210", residence.displaySubtitle)
        assertEquals("ct_CIU: 0121IV4B0004", residence.displayCiu)
        assertEquals("SHTN, TRECHO 1", residence.quadraDisplay)
        assertEquals("LT 8, POLO 3", residence.numeroCasaDisplay)
    }

    @Test
    fun testSetorMSulSpecificResidence() {
        val residence = CadastreLot(
            objectId = 178280L,
            ciu = "134III6A0511",
            setor = "SETOR M SUL",
            quadra = "QNM 3",
            conjunto = "CJ N",
            lote = "LT 21",
            endUsual = "SETOR M SUL QNM 3 CJ N LT 21",
            ra = "CEILÂNDIA",
            cep = "72215044",
            latitude = -15.823722,
            longitude = -48.101691
        )
        assertEquals("SETOR M SUL QNM 3 CJ N LT 21", residence.displayAddress)
        assertEquals("72215-044", residence.formattedCep)
        assertEquals("ct_CIU: 134III6A0511", residence.displayCiu)
        assertEquals("SETOR M SUL, QNM 3", residence.quadraDisplay)
        assertEquals("LT 21, CJ N", residence.numeroCasaDisplay)
        assertEquals("CEILÂNDIA • CEP 72215-044", residence.displaySubtitle)
    }

    @Test
    fun testAscendingLotSorting() {
        val lot1 = CadastreLot(objectId = 1, quadra = "QNM 3", conjunto = "CJ N", lote = "LT 2")
        val lot2 = CadastreLot(objectId = 2, quadra = "QNM 3", conjunto = "CJ N", lote = "LT 10")
        val lot3 = CadastreLot(objectId = 3, quadra = "QNM 3", conjunto = "CJ N", lote = "LT 1")
        val lot4 = CadastreLot(objectId = 4, quadra = "QNM 3", conjunto = "CJ N", lote = "LT 21")

        val sorted = listOf(lot1, lot2, lot3, lot4).sortedWith(
            compareBy<CadastreLot> { it.quadra }
                .thenBy { it.conjunto }
                .thenBy { it.loteNumberForSort }
                .thenBy { it.lote }
        )

        assertEquals("LT 1", sorted[0].lote)
        assertEquals("LT 2", sorted[1].lote)
        assertEquals("LT 10", sorted[2].lote)
        assertEquals("LT 21", sorted[3].lote)
    }
}

