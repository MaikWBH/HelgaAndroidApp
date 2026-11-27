package com.helga.android.ui.receipts

import androidx.annotation.StringRes
import com.helga.android.R
import com.helga.android.data.model.ProductCategory

/** Anzeigename einer Kategorie; `null` (noch nicht kategorisiert) → „Ohne Kategorie". */
@StringRes
fun ProductCategory?.labelRes(): Int = when (this) {
    null -> R.string.category_none
    ProductCategory.OBST_GEMUESE -> R.string.category_obst_gemuese
    ProductCategory.BROT_BACKWAREN -> R.string.category_brot_backwaren
    ProductCategory.MILCH_EIER -> R.string.category_milch_eier
    ProductCategory.FLEISCH_WURST -> R.string.category_fleisch_wurst
    ProductCategory.FISCH -> R.string.category_fisch
    ProductCategory.TIEFKUEHL -> R.string.category_tiefkuehl
    ProductCategory.TROCKENWARE -> R.string.category_trockenware
    ProductCategory.WUERZEN_BACKEN -> R.string.category_wuerzen_backen
    ProductCategory.FRUEHSTUECK -> R.string.category_fruehstueck
    ProductCategory.GETRAENKE -> R.string.category_getraenke
    ProductCategory.ALKOHOL -> R.string.category_alkohol
    ProductCategory.SUESSES_SNACKS -> R.string.category_suesses_snacks
    ProductCategory.FEINKOST -> R.string.category_feinkost
    ProductCategory.DROGERIE -> R.string.category_drogerie
    ProductCategory.HAUSHALT -> R.string.category_haushalt
    ProductCategory.SONSTIGES -> R.string.category_sonstiges
    ProductCategory.PFAND -> R.string.category_pfand
}
