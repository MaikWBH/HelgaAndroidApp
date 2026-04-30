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
    season_fit: str = ""
    created_at: int = 0


class IngredientRecord(SyncRecord):
    recipe_id: str
    position: int = 0
    quantity: float = 0
    unit: str = ""
    food: str = ""
    note: str = ""


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
