package com.ga.airdrop.feature.more2

/*
 * Static restricted-items content ported verbatim from
 * FigmaRestrictedItemsViewController / FigmaRestrictedItemsInfoViewController
 * (which mirror RN RestrictedItemsScreenView). Approved carve-out: content is
 * static, low polish priority.
 */

internal data class RestrictedItem(val title: String, val detail: String?)

internal enum class RestrictedCategory(
    val displayName: String,
    val headline: String,
    val intro: String,
    val items: List<RestrictedItem>,
    val carrierNote: String?,
    val batteryPolicy: String?,
) {
    PERMITTED(
        displayName = "Permitted",
        headline = "Permitted Commodities (General Goods)",
        intro = "Freely shippable items that meet carrier packaging and safety standards. " +
            "These items are normally admissible when declared correctly and duty/taxes " +
            "are paid.",
        items = listOf(
            RestrictedItem("Clothing, footwear and accessories", null),
            RestrictedItem("Books, documents and general household goods", null),
            RestrictedItem("Non-perishable foods", "Shelf-stable; subject to duties/taxes."),
            RestrictedItem("Consumer electronics", "Non-hazardous; see battery policy below."),
            RestrictedItem("Personal care items", "Non-aerosol, non-flammable."),
        ),
        carrierNote = null,
        batteryPolicy = "Intact consumer batteries and battery-powered devices are allowed " +
            "on commercial flights when properly packed, labeled and declared per IATA " +
            "Dangerous Goods regulations. Damaged, swollen, leaking or recalled batteries " +
            "are prohibited.",
    ),
    LICENSE_REQUIRED(
        displayName = "License Required",
        headline = "License Required Categories",
        intro = "These goods are legal to import into Jamaica only with valid permits or " +
            "licences from the relevant authority. Without proof of authorisation they will " +
            "be detained or refused entry.",
        items = listOf(
            RestrictedItem(
                "Agriculture",
                "Seeds, live or dried plants, soil/wood/plant materials and certain " +
                    "fertilizers (permits via RADA / Ministry of Agriculture & Fisheries).",
            ),
            RestrictedItem(
                "Animal Products & Furs",
                "Skins or leather of snakes, alligators, crocodiles, stingrays, wolves, " +
                    "bears, elephants and other protected wildlife; items containing " +
                    "endangered species such as sturgeon or beluga caviar (CITES/MOE " +
                    "licence required).",
            ),
            RestrictedItem(
                "Medical & Pharmaceutical",
                "Prescription medicines, medical devices not FDA-approved, certain " +
                    "over-the-counter drugs, prohormones, human growth hormones, steroids " +
                    "or synthetic versions (Ministry of Health permit).",
            ),
            RestrictedItem(
                "Security & Defense Equipment",
                "Body-armour/tactical vests, night-vision devices, thermal imaging or " +
                    "infrared optics, rifle scopes and laser aiming devices; edged weapons " +
                    "like swords (Ministry of National Security / Firearm Licensing " +
                    "Authority).",
            ),
            RestrictedItem(
                "Trade Board (Industrial/dual-use items)",
                "Dual-use industrial equipment, certain electronics/optics controlled under " +
                    "the Commerce Control List (CCL) where a BIS export licence is required.",
            ),
            RestrictedItem(
                "Animal & Veterinary Products",
                "Live animals, animal vaccines, cultures and certain biologics (permits " +
                    "via Veterinary Services Division).",
            ),
        ),
        carrierNote = null,
        batteryPolicy = null,
    ),
    RESTRICTED_COMMODITIES(
        displayName = "Restricted Commodities",
        headline = "Restricted Commodities (Carrier/Dangerous Goods)",
        intro = "These items may be carried only under strict packaging, quantity, labeling " +
            "or mode-of-transport conditions. The list below incorporates items from " +
            "AirDrop’s Restricted Items page that are classed as Dangerous Goods under " +
            "IATA/IMO rules.",
        items = listOf(
            RestrictedItem(
                "Hazardous chemicals and corrosives",
                "Acids, bio products (hazardous), chemicals (haz), corrosives and poisons.",
            ),
            RestrictedItem(
                "Flammable or oxidising substances",
                "Flammables, fireworks, oxidizers, paints (haz), perfumes/cosmetics (haz), " +
                    "solvents and adhesives.",
            ),
            RestrictedItem(
                "Gases and aerosols",
                "Compressed gases, aerosols, oxygen cylinders, dry ice or wet ice requiring " +
                    "special declarations.",
            ),
            RestrictedItem(
                "Lithium batteries and magnetised materials",
                "Power banks, device batteries, magnetized materials per IATA packing " +
                    "instructions; damaged batteries are prohibited.",
            ),
            RestrictedItem(
                "Infectious or biological substances",
                "Infectious substances, radioactive materials and biohazard samples.",
            ),
            RestrictedItem(
                "Other dangerous goods",
                "Laboratory reagents, dry ice and other items regulated under IATA " +
                    "Dangerous Goods rules.",
            ),
        ),
        carrierNote = "All packages will be handled as AirDrop Standard unless identified " +
            "otherwise in address line 2.",
        batteryPolicy = null,
    ),
    PROHIBITED(
        displayName = "Prohibited",
        headline = "Prohibited Articles (Non-Admissible)",
        intro = "These items are not shippable under any circumstance because they are " +
            "illegal to export from the United States or import into Jamaica.",
        items = listOf(
            RestrictedItem(
                "Firearms, ammunition, explosives and weapon accessories",
                "Includes replicas and military equipment.",
            ),
            RestrictedItem(
                "Illegal drugs/narcotics and paraphernalia",
                "Also includes poisonous or toxic substances, explosives and gun powder.",
            ),
            RestrictedItem(
                "Counterfeit currency, counterfeit goods, lottery tickets and gambling devices",
                null,
            ),
            RestrictedItem(
                "Unidentifiable substances or chemicals",
                "Radioactive elements, lab reagents, biologics and medical specimens " +
                    "without authorisation.",
            ),
            RestrictedItem(
                "Live or dead animals or insects",
                "Animal parts or fur products without CITES permits, including Brazilian " +
                    "rosewood and certain reptile skins.",
            ),
            RestrictedItem(
                "Stun guns/tasers, tear gas/mace/pepper spray and handcuffs",
                "Also includes law-enforcement striking weapons such as batons or billy clubs.",
            ),
            RestrictedItem(
                "Dual-use controlled items",
                "Items controlled under the U.S. Munitions List or Commerce Control List " +
                    "without an export licence, including night-vision devices, flight " +
                    "helmets and tactical gear.",
            ),
        ),
        carrierNote = null,
        batteryPolicy = null,
    ),
    CONDITIONAL_COMMODITIES(
        displayName = "Conditional Commodities",
        headline = "Conditional Commodities (Special Handling)",
        intro = "Items that may be accepted on a case-by-case basis with quantity limits, " +
            "special packaging or additional documentation. Many of these appear on " +
            "AirDrop’s restricted list and require careful review.",
        items = listOf(
            RestrictedItem(
                "Non-prescription medications",
                "Dietary supplements and cosmetics not properly labeled in English or " +
                    "without FDA/MOH approval.",
            ),
            RestrictedItem(
                "Perishable goods",
                "Including Meals Ready to Eat (MREs) and items requiring refrigeration or " +
                    "cold-chain handling.",
            ),
            RestrictedItem(
                "Pesticides and agricultural chemicals",
                "Herbicides, fungicides and other agricultural chemicals.",
            ),
            RestrictedItem(
                "Self-propelled vehicles and engines",
                "Fuel-powered equipment must be drained of fuel and may need permits.",
            ),
            RestrictedItem(
                "High-value electronics and fragile items",
                "Must have proof of purchase / valuation and recommended insurance.",
            ),
            RestrictedItem(
                "Cultural artefacts, art and antiques",
                "Subject to customs inspection and possible licenses.",
            ),
        ),
        carrierNote = null,
        batteryPolicy = null,
    ),
}
