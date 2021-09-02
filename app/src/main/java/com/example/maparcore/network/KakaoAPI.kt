package com.example.maparcore.network

import com.example.maparcore.data.ResultSearchKeyword
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface KakaoAPI {
    @GET("v2/local/search/keyword.json")    // Keyword.json의 정보를 받아옴
    fun getSearchKeyword(
        @Header("Authorization") key: String,     // 카카오 API 인증키 [필수]
        @Query("query") query: String,             // 검색을 원하는 질의어 [필수]
        @Query("x") x : String = "37.5200332",
        @Query("y") y : String ="127.0247703",
        @Query("radius") radius : Int = 1000
        // 매개변수 추가 가능
        // @Query("category_group_code") category: String
        // https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword

    ): Call<ResultSearchKeyword>    // 받아온 정보가 ResultSearchKeyword 클래스의 구조로 담김
}