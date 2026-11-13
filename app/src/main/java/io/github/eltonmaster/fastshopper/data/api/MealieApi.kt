package io.github.eltonmaster.fastshopper.data.api

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MealieApi {
    @GET("api/users/self")
    suspend fun getSelf(): UserSelf

    @GET("api/households/shopping/lists")
    suspend fun getShoppingLists(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = -1,
        @Query("orderBy") orderBy: String = "name",
        @Query("orderDirection") orderDirection: String = "asc",
    ): Page<ShoppingListSummary>

    @GET("api/households/shopping/lists/{id}")
    suspend fun getShoppingList(@Path("id") id: String): ShoppingList

    @GET("api/foods")
    suspend fun getFoods(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = -1,
    ): Page<FoodRef>

    @POST("api/foods")
    suspend fun createFood(@Body food: FoodCreate): FoodRef

    @GET("api/units")
    suspend fun getUnits(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = -1,
    ): Page<UnitRef>

    @POST("api/units")
    suspend fun createUnit(@Body unit: UnitCreate): UnitRef

    @GET("api/groups/labels")
    suspend fun getLabels(
        @Query("page") page: Int = 1,
        @Query("perPage") perPage: Int = -1,
    ): Page<LabelRef>

    @POST("api/groups/labels")
    suspend fun createLabel(@Body label: LabelCreate): LabelRef

    @POST("api/households/shopping/items")
    suspend fun createItem(@Body item: ItemWrite): ResponseBody

    @PUT("api/households/shopping/items")
    suspend fun updateItems(@Body items: List<ItemWrite>): ResponseBody

    @DELETE("api/households/shopping/items")
    suspend fun deleteItems(@Query("ids") ids: List<String>): ResponseBody

    // ---- List management ---------------------------------------------------------------------

    @POST("api/households/shopping/lists")
    suspend fun createShoppingList(@Body list: ShoppingListCreate): ShoppingListSummary

    /** The list exactly as Mealie returns it, so a rename can send every field back unchanged. */
    @GET("api/households/shopping/lists/{id}")
    suspend fun getShoppingListJson(@Path("id") id: String): JsonObject

    /** Replaces the whole list, items included. Build the body with [io.github.eltonmaster.fastshopper.data.ListEdit.renamed]. */
    @PUT("api/households/shopping/lists/{id}")
    suspend fun updateShoppingList(@Path("id") id: String, @Body list: JsonObject): ResponseBody

    @DELETE("api/households/shopping/lists/{id}")
    suspend fun deleteShoppingList(@Path("id") id: String): ResponseBody
}
