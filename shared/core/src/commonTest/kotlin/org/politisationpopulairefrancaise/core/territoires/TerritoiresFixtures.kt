package org.politisationpopulairefrancaise.core.territoires

import org.politisationpopulairefrancaise.core.publication.ElusDownload
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** Small documents in the format of the server (docs/FORMATS.md, section 4 nonies), with real public figures. */
object TerritoiresFixtures {
    private const val SOURCES =
        """[{"id":"popref-2023","kind":"population","name":"Populations de référence 2023 (Insee)","url":"https://www.insee.fr/fr/statistiques/8681011","licence":"Licence Ouverte 2.0","vintage":2023,"date":"2026-01-01"},""" +
            """{"id":"reu-2026-02-11","kind":"electorate","name":"Répertoire électoral unique, électeurs inscrits (Insee)","url":"https://www.insee.fr/fr/statistiques/8888278","licence":"Licence Ouverte 2.0","date":"2026-02-11"},""" +
            """{"id":"insee-com-2023","kind":"population","name":"Populations de référence 2023 des collectivités d'outre-mer (Insee)","licence":"Licence Ouverte 2.0","vintage":2023,"date":"2026-01-01"}]"""

    val rhonePackage: String =
        """{"format":1,"example":true,"code":"69","name":"Rhône","outreMer":false,"region":"84","geography":"2026-01-01","sources":$SOURCES,""" +
            """"communes":[""" +
            """{"code":"69123","name":"Lyon","of":"de Lyon","postalCodes":["69001","69002"],"collectivite":"69M","population":519127,"populationTotale":525314,"inscrits":318916,"inscritsComplementaires":2445},""" +
            """{"code":"69264","name":"Villefranche-sur-Saône","of":"de Villefranche-sur-Saône","postalCodes":["69400"],"epci":"200040590","collectivite":"69D","population":37000,"inscrits":21000,"inscritsComplementaires":300},""" +
            """{"code":"69114","name":"Porte des Pierres Dorées","of":"de Porte des Pierres Dorées","postalCodes":["69400"],"epci":"200040590","collectivite":"69D","population":4000,"inscrits":3000,"inscritsComplementaires":10,"computed":["population","inscrits","inscritsComplementaires"],"formerCodes":["69159"]},""" +
            """{"code":"","name":"Sans code"},{"code":"69999","population":12}""" +
            """],"arrondissements":[{"code":"69381","name":"Lyon 1er Arrondissement","of":"de Lyon 1er Arrondissement","commune":"69123","postalCodes":["69001"],"population":29000,"inscrits":18000}],""" +
            """"epcis":[{"code":"200040590","name":"Communauté d'agglomération Villefranche Beaujolais Saône","of":"de la Communauté d'agglomération Villefranche Beaujolais Saône","nature":"CA","departements":["01","69"],"communes":["01194","69114","69264"],"population":76000,"inscrits":47000,"computed":["population","inscrits"]}],""" +
            """"unknownField":{"a":1}}"""

    val communesIndex: String =
        """{"format":1,"example":true,"code":"communes","geography":"2026-01-01","communes":[["01194","Jassans-Riottier","01"],["2A004","Ajaccio","2A"],["69123","Lyon","69"],["69264","Villefranche-sur-Saône","69"],["bad","x","69"]]}"""

    fun national(rhoneSha: String = sha(rhonePackage), rhoneBytes: Int = rhonePackage.encodeToByteArray().size, builtOn: String = "2026-02-11"): String =
        """{"format":1,"example":true,"partial":true,"geography":"2026-01-01","builtOn":"$builtOn","sources":$SOURCES,""" +
            """"france":{"name":"France","population":68094280,"populationPerimeter":"France hors Mayotte et hors collectivités d'outre-mer","inscrits":48154748,"inscritsPerimeter":"France et collectivités d'outre-mer, hors Nouvelle-Calédonie"},""" +
            """"regions":[{"code":"84","name":"Auvergne-Rhône-Alpes","of":"d'Auvergne-Rhône-Alpes","population":8235923,"inscrits":5713379},{"code":"94","name":"Corse","of":"de la Corse","population":355486,"inscrits":253064,"heldBy":"20R"}],""" +
            """"departements":[{"code":"69","name":"Rhône","of":"du Rhône","region":"84","collectivites":["69D","69M"],"population":1914667,"populationTotale":1940015,"inscrits":1227213,"inscritsComplementaires":7992,"spread":"counts"},""" +
            """{"code":"2A","name":"Corse-du-Sud","of":"de la Corse-du-Sud","region":"94","collectivites":["20R"],"population":168306,"inscrits":120000,"spread":"counts"},""" +
            """{"code":"976","name":"Mayotte","of":"de Mayotte","region":"06","collectivites":["976R"],"inscrits":109512,"spread":"counts"},""" +
            """{"code":"20","name":"Code impossible","spread":"counts"}],""" +
            """"collectivites":[{"code":"69D","name":"Département du Rhône","of":"du Département du Rhône","special":false,"region":"84","departements":["69"],"population":464667,"inscrits":327213,"computed":["population","inscrits"]},""" +
            """{"code":"69M","name":"Métropole de Lyon","of":"de la Métropole de Lyon","special":true,"region":"84","departements":["69"],"population":1450000,"inscrits":900000,"computed":["population","inscrits"]},""" +
            """{"code":"20R","name":"Collectivité de Corse","of":"de la Collectivité de Corse","special":true,"region":"94","departements":["2A","2B"],"population":355486,"inscrits":253064,"computed":["population","inscrits"]}],""" +
            """"outreMer":[{"code":"975","name":"Saint-Pierre-et-Miquelon","of":"de Saint-Pierre-et-Miquelon","population":5790,"inscrits":5059,"inscritsComplementaires":4,"spread":"counts","sources":{"population":"insee-com-2023"}},""" +
            """{"code":"988","name":"Nouvelle-Calédonie","of":"de la Nouvelle-Calédonie","spread":"to-confirm"}],""" +
            """"packages":[{"code":"69","name":"Rhône","sha256":"$rhoneSha","bytes":$rhoneBytes,"postalCodes":["69001","69002","69400"]},""" +
            """{"code":"communes","name":"Toutes les communes","sha256":"${sha(communesIndex)}","bytes":${communesIndex.encodeToByteArray().size},"postalCodes":[]}]}"""

    fun sha(text: String): String = TerritoiresDigest.sha256Hex(text.encodeToByteArray())

    /** A server answering from [documents] (code, or null for the national level, to body); counts the calls. */
    class FakeServer(var documents: Map<String?, String>) : RemoteEvents {
        val asked = mutableListOf<String?>()
        var reachable = true

        override suspend fun fetchTerritoires(code: String?, etag: String?): ElusDownload {
            asked += code
            if (!reachable) return ElusDownload.Unavailable()
            val body = documents[code] ?: return if (code == null) ElusDownload.Unavailable() else ElusDownload.UnknownPackage
            if (etag != null && etag == sha(body)) return ElusDownload.NotModified
            return ElusDownload.Body(body.encodeToByteArray())
        }

        override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Unavailable()

        override suspend fun publish(event: SignedEvent): PublicationResult = throw UnsupportedOperationException()
    }
}
