package com.example.data.model

data class WmsLayerInfo(
    val id: String,
    val name: String,
    val title: String,
    val queryable: Boolean = true,
    val abstractText: String = ""
)

data class WmsServiceInfo(
    val title: String = "Cadastro Territorial Geoportal IDE DF",
    val serviceName: String = "WMS",
    val abstractText: String = "Serviço WMS oficial do Cadastro Territorial do Distrito Federal",
    val serviceUrl: String = "https://geoportal.ide.df.gov.br/server/services/Publico/cadastro_territorial_geoportal/MapServer/WMSServer",
    val version: String = "1.3.0",
    val supportedCrs: List<String> = listOf("EPSG:4326", "EPSG:31983", "CRS:84"),
    val supportedFormats: List<String> = listOf("image/png", "image/png32", "image/jpeg", "image/svg+xml"),
    val layers: List<WmsLayerInfo> = listOf(
        WmsLayerInfo("0", "0", "Localidades"),
        WmsLayerInfo("1", "1", "Setor"),
        WmsLayerInfo("2", "2", "Quadras"),
        WmsLayerInfo("3", "3", "Conjunto"),
        WmsLayerInfo("4", "4", "Ocupações Identificadas"),
        WmsLayerInfo("5", "5", "Lotes Registrados"),
        WmsLayerInfo("6", "6", "Unidades Autônomas"),
        WmsLayerInfo("7", "7", "Telheiro"),
        WmsLayerInfo("8", "8", "Piscinas"),
        WmsLayerInfo("9", "9", "Edificação"),
        WmsLayerInfo("10", "10", "Pavimento"),
        WmsLayerInfo("11", "11", "Marquises"),
        WmsLayerInfo("12", "12", "Cobertura"),
        WmsLayerInfo("13", "13", "Campos e Quadras Poliesportivas"),
        WmsLayerInfo("14", "14", "Cercas e Muros")
    ),
    val dfBoundingBox: DfBoundingBox = DfBoundingBox(
        minLat = -16.05,
        minLon = -48.28,
        maxLat = -15.50,
        maxLon = -47.30
    )
)

data class DfBoundingBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double
)
