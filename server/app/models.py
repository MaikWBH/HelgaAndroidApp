from typing import List, Optional
from pydantic import BaseModel


class SyncRecord(BaseModel):
    id: str
    updated_at: int
    deleted: int = 0


class RecipeRecord(SyncRecord):
    slug: str = ""
    name: str = ""
    description: str = ""
    recipe_yield: str = ""
    prep_time: str = ""
    cook_time: str = ""
    total_time: str = ""
    image_path: str = ""
    source_url: str = ""
    rating: int = 0
    protein_type: str = ""
    effort: str = ""
    cuisine: str = ""
    meal_type: str = ""
    meal_slot: str = "other"
    season_fit: str = ""
    created_at: int = 0


class IngredientRecord(SyncRecord):
    recipe_id: str
    position: int = 0
    quantity: float = 0
    unit: str = ""
    food: str = ""
    note: str = ""
    off_barcode: str = ""


class InstructionRecord(SyncRecord):
    recipe_id: str
    position: int = 0
    text: str = ""


class TagRecord(SyncRecord):
    recipe_id: str
    name: str = ""


class CategoryRecord(SyncRecord):
    recipe_id: str
    name: str = ""


class FoodRecord(SyncRecord):
    name: str = ""


class UnitRecord(SyncRecord):
    name: str = ""


class ProductUnitRecord(SyncRecord):
    product_name: str = ""
    unit_name: str = ""
    sort_order: int = 0


class StoreRecord(SyncRecord):
    name: str = ""
    is_active: int = 0


class StoreAisleRecord(SyncRecord):
    store_id: str
    aisle_name: str = ""
    sort_order: int = 0


class AisleProductRecord(SyncRecord):
    aisle_name: str = ""
    product_name: str = ""
    store_id: str = ""


class ShoppingListRecord(SyncRecord):
    name: str = ""
    is_active: int = 0
    is_default_weekplan: int = 0
    is_default_recipe: int = 0


class ShoppingItemRecord(SyncRecord):
    list_id: str
    name: str = ""
    quantity: float = 1
    unit: str = ""
    aisle: str = ""
    source: str = "manual"
    is_checked: int = 0
    sort_order: int = 0
    origins: str = "[]"
    off_barcode: str = ""
    off_product_id: str = ""
    price_estimate: float = 0.0
    price_last_checked: int = 0


class ShoppingListStapleRecord(SyncRecord):
    list_id: str
    name: str = ""
    quantity: float = 1
    sort_order: int = 0


class WeekplanDayRecord(SyncRecord):
    plan_date: str = ""
    note: str = ""


class WeekplanRecipeRecord(SyncRecord):
    weekplan_day_id: str
    recipe_id: str
    position: int = 0


class WeekplanExtraRecord(SyncRecord):
    weekplan_day_id: str
    item_text: str = ""
    position: int = 0


class RecipeHistoryRecord(SyncRecord):
    recipe_id: str
    planned_date: str = ""


class QuickEmojiRecord(SyncRecord):
    emoji: str = ""
    food: str = ""
    quantity: float = 1
    unit: str = ""
    sort_order: int = 0


class AppSettingRecord(SyncRecord):
    value: str = ""


class WeekplanSettingsRecord(SyncRecord):
    plan_days: int = 7
    shopping_day: int = 0


class WeekplanConstraintsRecord(SyncRecord):
    max_meat_per_week: int = 3
    min_vegetarian_per_week: int = 2
    max_repeat_days: int = 14


class MonthlyBudgetRecord(SyncRecord):
    amount: float = 0.0
    warn_threshold: float = 0.8


class OffProductRecord(SyncRecord):
    barcode: str = ""
    name: str = ""
    brand: str = ""
    categories: str = "[]"
    kcal_per_unit: float = 0.0
    proteins: float = 0.0
    fats: float = 0.0
    carbs: float = 0.0
    nutri_score: str = ""
    nova: int = 0
    eco_score: str = ""
    allergenes: str = "[]"
    additives: str = "[]"
    is_organic: int = 0
    vegan: int = 0
    vegetarian: int = 0
    image_path: str = ""
    is_favorite: int = 0


class IngredientMappingRecord(SyncRecord):
    ingredient_name: str = ""
    off_product_id: str = ""
    off_barcode: str = ""
    display_name: str = ""


class ProductPriceRecord(SyncRecord):
    off_product_id: str = ""
    store_name: str = ""
    currency: str = "EUR"
    price: float = 0.0
    unit: str = ""
    last_checked_at: int = 0


class ProductPurchaseRecord(SyncRecord):
    shopping_item_id: str = ""
    off_product_id: str = ""
    quantity_purchased: float = 1.0
    price_paid: float = 0.0
    store_name: str = ""
    purchase_date: int = 0


class ReceiptRecord(SyncRecord):
    store_id: str = ""
    store_name: str = ""
    shopping_list_id: str = ""
    purchase_date: int = 0
    total_amount: float = 0.0
    currency: str = "EUR"
    image_path: str = ""
    # local_image_uri wird NICHT synchronisiert (gerätelokaler Pfad) – siehe sync.py.
    raw_ocr_text: str = ""
    status: str = "scanned"


class ReceiptItemRecord(SyncRecord):
    receipt_id: str
    position: int = 0
    raw_text: str = ""
    name: str = ""
    quantity: float = 1.0
    unit_price: float = 0.0
    total_price: float = 0.0
    matched_shopping_item_id: str = ""
    match_status: str = ""


# ── Sync-Payload ────────────────────────────────────────────────────────────

class SyncPayload(BaseModel):
    recipes: List[RecipeRecord] = []
    recipe_ingredients: List[IngredientRecord] = []
    recipe_instructions: List[InstructionRecord] = []
    recipe_tags: List[TagRecord] = []
    recipe_categories: List[CategoryRecord] = []
    foods: List[FoodRecord] = []
    units: List[UnitRecord] = []
    product_units: List[ProductUnitRecord] = []
    stores: List[StoreRecord] = []
    store_aisles: List[StoreAisleRecord] = []
    aisle_products: List[AisleProductRecord] = []
    shopping_lists: List[ShoppingListRecord] = []
    shopping_items: List[ShoppingItemRecord] = []
    shopping_list_staples: List[ShoppingListStapleRecord] = []
    weekplan_days: List[WeekplanDayRecord] = []
    weekplan_recipes: List[WeekplanRecipeRecord] = []
    weekplan_extras: List[WeekplanExtraRecord] = []
    recipe_history: List[RecipeHistoryRecord] = []
    quick_emojis: List[QuickEmojiRecord] = []
    app_settings: List[AppSettingRecord] = []
    weekplan_settings: List[WeekplanSettingsRecord] = []
    weekplan_constraints: List[WeekplanConstraintsRecord] = []
    off_products: List[OffProductRecord] = []
    ingredient_product_mappings: List[IngredientMappingRecord] = []
    product_prices: List[ProductPriceRecord] = []
    product_purchases: List[ProductPurchaseRecord] = []
    receipts: List[ReceiptRecord] = []
    receipt_items: List[ReceiptItemRecord] = []
    monthly_budgets: List[MonthlyBudgetRecord] = []


class SyncPullResponse(SyncPayload):
    server_ts: int


class SyncPushRequest(SyncPayload):
    client_ts: int = 0


# ── KI-Requests ─────────────────────────────────────────────────────────────

class AiGenerateRequest(BaseModel):
    prompt: str
    available_tags: List[str] = []
    custom_instructions: Optional[str] = None


class AiRemixRequest(BaseModel):
    recipe_name: str
    recipe_description: str = ""
    recipe_ingredients: List[str] = []
    recipe_instructions: List[str] = []
    remix_prompt: str
    available_tags: List[str] = []


class AiClassifyRequest(BaseModel):
    name: str
    description: str = ""
    tags: List[str] = []
    ingredients: List[str] = []


class AiUrlImportRequest(BaseModel):
    url: str


class ImportedIngredient(BaseModel):
    food: str = ""
    quantity: float = 0.0
    unit: str = ""
    note: str = ""


class ImportedInstruction(BaseModel):
    text: str = ""


class AiImportResponse(BaseModel):
    name: str = ""
    description: str = ""
    recipe_yield: str = ""
    prep_time: str = ""
    cook_time: str = ""
    total_time: str = ""
    cuisine: str = ""
    meal_slot: str = "other"
    effort: str = ""
    protein_type: str = ""
    season_fit: str = ""
    source_url: str = ""
    image_url: str = ""
    ingredients: List[ImportedIngredient] = []
    instructions: List[ImportedInstruction] = []
    tags: List[str] = []


class WeekplanGenerateRequest(BaseModel):
    start_date: str
    plan_days: int = 7
    max_meat_per_week: int = 3
    min_vegetarian_per_week: int = 2
    max_repeat_days: int = 14


class WeekplanAssignmentDto(BaseModel):
    date: str
    recipe_id: str
    recipe_name: str = ""


class WeekplanGenerateResponse(BaseModel):
    assignments: List[WeekplanAssignmentDto] = []


# ── OFF-Lookups ──────────────────────────────────────────────────────────────

class OffLookupBarcodeRequest(BaseModel):
    barcode: str


class OffLookupBarcodeResponse(OffProductRecord):
    pass


class OffSearchRequest(BaseModel):
    query: str
    limit: int = 5


class OffSearchResponse(BaseModel):
    products: List[OffProductRecord] = []


# ── Receipt Reconciliation (Phase 4) ─────────────────────────────────────────

class ReconcileShoppingItem(BaseModel):
    id: str
    name: str = ""
    quantity: float = 1.0
    unit: str = ""


class ReconcileReceiptItem(BaseModel):
    id: str
    name: str = ""
    raw_text: str = ""
    total_price: float = 0.0


class ReceiptReconcileRequest(BaseModel):
    checked_items: List[ReconcileShoppingItem] = []
    receipt_items: List[ReconcileReceiptItem] = []


class ReconcileMatch(BaseModel):
    shopping_item_id: str = ""
    receipt_item_id: str = ""


class ReceiptReconcileResponse(BaseModel):
    matches: List[ReconcileMatch] = []
    unexpected: List[str] = []
    missing: List[str] = []


# ── Receipt Parsing (KI-Vision) ──────────────────────────────────────────────
# Liest einen fotografierten Kassenbon per Vision-Modell aus. Robuster als die
# On-Device-OCR, weil das Modell Layout, Mengen und Preise direkt versteht.

class ReceiptParseRequest(BaseModel):
    image_base64: str
    mime_type: str = "image/jpeg"


class ReceiptParseItem(BaseModel):
    name: str = ""
    quantity: float = 1.0
    unit_price: float = 0.0
    total_price: float = 0.0


class ReceiptParseResponse(BaseModel):
    store_name: str = ""
    purchase_date: int = 0  # Unix-ms, 0 wenn nicht erkennbar
    total_amount: float = 0.0
    items: List[ReceiptParseItem] = []
