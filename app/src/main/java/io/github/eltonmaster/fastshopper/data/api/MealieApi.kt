package io.github.eltonmaster.fastshopper.data.api

import retrofit2.http.GET
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
}
