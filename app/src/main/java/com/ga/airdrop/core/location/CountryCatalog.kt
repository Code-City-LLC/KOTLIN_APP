package com.ga.airdrop.core.location

import java.util.Locale

/** A canonical ISO country row shared by country pickers and API payloads. */
data class CountryEntry(
    val isoCode: String,
    val name: String,
    val flagEmoji: String,
    val dialCode: String?,
) {
    val display: String
        get() = if (dialCode == null) {
            "$flagEmoji  $name"
        } else {
            "$flagEmoji  $name  ($dialCode)"
        }
}

/**
 * Frozen Swift CountryCatalog parity.
 *
 * The catalog covers every ISO 3166-1 alpha-2 country exposed by the JVM,
 * keeps canonical English names for backend payloads, and exposes decorated
 * display rows for UI pickers. Jamaica, United States, Canada, and United
 * Kingdom are anchored first; other English-speaking regions follow before
 * the remaining alphabetical countries.
 */
object CountryCatalog {
    val all: List<CountryEntry> by lazy(LazyThreadSafetyMode.PUBLICATION, ::buildAll)

    val displayOptions: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        all.map(CountryEntry::display)
    }

    private val byDisplay: Map<String, CountryEntry> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        all.associateBy(CountryEntry::display)
    }
    private val byName: Map<String, CountryEntry> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        all.associateBy { it.name.lowercase(Locale.US) }
    }
    private val byIso: Map<String, CountryEntry> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        all.associateBy(CountryEntry::isoCode)
    }

    fun entryForDisplay(display: String): CountryEntry? = byDisplay[display.trim()]

    fun entryForName(name: String): CountryEntry? =
        byName[name.trim().lowercase(Locale.US)]

    fun entryForIso(isoCode: String): CountryEntry? =
        byIso[isoCode.trim().uppercase(Locale.US)]

    /** Resolve a decorated row, canonical English name, or ISO code. */
    fun entry(value: String): CountryEntry? =
        entryForDisplay(value) ?: entryForName(value) ?: entryForIso(value)

    /** Decorated picker value for a canonical API country name. */
    fun displayNameFor(country: String): String = entry(country)?.display ?: country.trim()

    /** Canonical English backend value for a picker display row. */
    fun canonicalName(value: String): String = entry(value)?.name ?: value.trim()

    /** Unknown countries default to requiring postal data (the safe branch). */
    fun requiresPostalCode(country: String): Boolean =
        entry(country)?.let { requiresPostalCodeForIso(it.isoCode) } ?: true

    fun requiresPostalCodeForIso(isoCode: String): Boolean =
        isoCode.trim().uppercase(Locale.US) !in countriesWithoutPostal

    private fun buildAll(): List<CountryEntry> = Locale.getISOCountries()
        .asSequence()
        .map { it.uppercase(Locale.US) }
        .filter { it.length == 2 }
        .distinct()
        .map { code ->
            CountryEntry(
                isoCode = code,
                name = Locale("", code).getDisplayCountry(Locale.US).ifBlank { code },
                flagEmoji = flagFor(code),
                dialCode = dialCodes[code],
            )
        }
        .sortedWith(
            compareBy<CountryEntry>({ sortTier(it) }, { sortKey(it) }),
        )
        .toList()

    private fun sortTier(entry: CountryEntry): Int = when {
        entry.isoCode in anchorPriority -> 0
        entry.isoCode in englishSpeakingIso -> 1
        else -> 2
    }

    private fun sortKey(entry: CountryEntry): String =
        anchorPriority[entry.isoCode]?.toString()?.padStart(3, '0')
            ?: entry.name.lowercase(Locale.US)

    private fun flagFor(isoCode: String): String = buildString {
        isoCode.uppercase(Locale.US).forEach { letter ->
            appendCodePoint(REGIONAL_INDICATOR_A + (letter - 'A'))
        }
    }

    private const val REGIONAL_INDICATOR_A = 0x1F1E6

    private val anchorPriority = mapOf(
        "JM" to 0,
        "US" to 1,
        "CA" to 2,
        "GB" to 3,
    )

    private val englishSpeakingIso = setOf(
        // Caribbean / Atlantic
        "AI", "AG", "BS", "BB", "BZ", "BM", "DM", "GD", "GY", "JM", "KN",
        "LC", "MS", "PR", "TC", "TT", "VC", "VG", "VI",
        // North America / British Isles
        "CA", "GB", "IE", "IM", "JE", "GG", "US",
        // Africa
        "BW", "CM", "ET", "GH", "GM", "KE", "LR", "LS", "MW", "MU", "NA",
        "NG", "RW", "SC", "SD", "SL", "SS", "SZ", "TZ", "UG", "ZM", "ZW",
        // Oceania
        "AU", "FJ", "KI", "MH", "FM", "NR", "NZ", "PG", "PW", "SB", "TO",
        "TV", "VU", "WS",
        // Asia
        "BN", "HK", "IN", "MY", "PK", "PH", "SG",
        // Europe / dependencies
        "MT", "GI", "FK", "SH", "PN", "NF",
    )

    private val countriesWithoutPostal = setOf(
        "AG", "AO", "AW", "BJ", "BO", "BS", "BW", "BZ", "CD", "CF", "CG", "CI",
        "CK", "CM", "DJ", "DM", "ER", "FJ", "GD", "GH", "GM", "GQ", "GY", "HK",
        "JM", "KI", "KM", "KN", "KP", "LC", "ML", "MO", "MR", "MW", "NR", "NU",
        "PA", "QA", "RW", "SB", "SC", "SL", "SO", "SR", "ST", "SY", "TG", "TK",
        "TL", "TO", "TT", "TV", "UG", "VC", "VU", "WS", "YE", "ZW",
    )

    /** ISO alpha-2 to E.164 international dialing prefix, matching Swift. */
    private val dialCodes = mapOf(
        "AF" to "+93", "AL" to "+355", "DZ" to "+213", "AS" to "+1684", "AD" to "+376", "AO" to "+244",
        "AI" to "+1264", "AQ" to "+672", "AG" to "+1268", "AR" to "+54", "AM" to "+374", "AW" to "+297",
        "AU" to "+61", "AT" to "+43", "AZ" to "+994", "BS" to "+1242", "BH" to "+973", "BD" to "+880",
        "BB" to "+1246", "BY" to "+375", "BE" to "+32", "BZ" to "+501", "BJ" to "+229", "BM" to "+1441",
        "BT" to "+975", "BO" to "+591", "BA" to "+387", "BW" to "+267", "BR" to "+55", "IO" to "+246",
        "BN" to "+673", "BG" to "+359", "BF" to "+226", "BI" to "+257", "KH" to "+855", "CM" to "+237",
        "CA" to "+1", "CV" to "+238", "KY" to "+1345", "CF" to "+236", "TD" to "+235", "CL" to "+56",
        "CN" to "+86", "CX" to "+61", "CC" to "+61", "CO" to "+57", "KM" to "+269", "CG" to "+242",
        "CD" to "+243", "CK" to "+682", "CR" to "+506", "CI" to "+225", "HR" to "+385", "CU" to "+53",
        "CY" to "+357", "CZ" to "+420", "DK" to "+45", "DJ" to "+253", "DM" to "+1767", "DO" to "+1809",
        "EC" to "+593", "EG" to "+20", "SV" to "+503", "GQ" to "+240", "ER" to "+291", "EE" to "+372",
        "ET" to "+251", "FK" to "+500", "FO" to "+298", "FJ" to "+679", "FI" to "+358", "FR" to "+33",
        "GF" to "+594", "PF" to "+689", "GA" to "+241", "GM" to "+220", "GE" to "+995", "DE" to "+49",
        "GH" to "+233", "GI" to "+350", "GR" to "+30", "GL" to "+299", "GD" to "+1473", "GP" to "+590",
        "GU" to "+1671", "GT" to "+502", "GN" to "+224", "GW" to "+245", "GY" to "+592", "HT" to "+509",
        "HN" to "+504", "HK" to "+852", "HU" to "+36", "IS" to "+354", "IN" to "+91", "ID" to "+62",
        "IR" to "+98", "IQ" to "+964", "IE" to "+353", "IL" to "+972", "IT" to "+39", "JM" to "+1876",
        "JP" to "+81", "JO" to "+962", "KZ" to "+7", "KE" to "+254", "KI" to "+686", "KP" to "+850",
        "KR" to "+82", "KW" to "+965", "KG" to "+996", "LA" to "+856", "LV" to "+371", "LB" to "+961",
        "LS" to "+266", "LR" to "+231", "LY" to "+218", "LI" to "+423", "LT" to "+370", "LU" to "+352",
        "MO" to "+853", "MK" to "+389", "MG" to "+261", "MW" to "+265", "MY" to "+60", "MV" to "+960",
        "ML" to "+223", "MT" to "+356", "MH" to "+692", "MQ" to "+596", "MR" to "+222", "MU" to "+230",
        "YT" to "+262", "MX" to "+52", "FM" to "+691", "MD" to "+373", "MC" to "+377", "MN" to "+976",
        "ME" to "+382", "MS" to "+1664", "MA" to "+212", "MZ" to "+258", "MM" to "+95", "NA" to "+264",
        "NR" to "+674", "NP" to "+977", "NL" to "+31", "NC" to "+687", "NZ" to "+64", "NI" to "+505",
        "NE" to "+227", "NG" to "+234", "NU" to "+683", "NF" to "+672", "MP" to "+1670", "NO" to "+47",
        "OM" to "+968", "PK" to "+92", "PW" to "+680", "PA" to "+507", "PG" to "+675", "PY" to "+595",
        "PE" to "+51", "PH" to "+63", "PN" to "+870", "PL" to "+48", "PT" to "+351", "PR" to "+1787",
        "QA" to "+974", "RE" to "+262", "RO" to "+40", "RU" to "+7", "RW" to "+250", "KN" to "+1869",
        "LC" to "+1758", "PM" to "+508", "VC" to "+1784", "WS" to "+685", "SM" to "+378", "ST" to "+239",
        "SA" to "+966", "SN" to "+221", "RS" to "+381", "SC" to "+248", "SL" to "+232", "SG" to "+65",
        "SK" to "+421", "SI" to "+386", "SB" to "+677", "SO" to "+252", "ZA" to "+27", "ES" to "+34",
        "LK" to "+94", "SD" to "+249", "SR" to "+597", "SJ" to "+47", "SZ" to "+268", "SE" to "+46",
        "CH" to "+41", "SY" to "+963", "TW" to "+886", "TJ" to "+992", "TZ" to "+255", "TH" to "+66",
        "TL" to "+670", "TG" to "+228", "TK" to "+690", "TO" to "+676", "TT" to "+1868", "TN" to "+216",
        "TR" to "+90", "TM" to "+993", "TC" to "+1649", "TV" to "+688", "UG" to "+256", "UA" to "+380",
        "AE" to "+971", "GB" to "+44", "US" to "+1", "UY" to "+598", "UZ" to "+998", "VU" to "+678",
        "VA" to "+379", "VE" to "+58", "VN" to "+84", "VG" to "+1284", "VI" to "+1340", "WF" to "+681",
        "EH" to "+212", "YE" to "+967", "ZM" to "+260", "ZW" to "+263",
    )
}
