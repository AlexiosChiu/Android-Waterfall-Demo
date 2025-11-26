package com.example.waterfall.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.waterfall.R
import com.example.waterfall.adapter.FeedAdapter
import com.example.waterfall.data.FeedItem

class HomePageActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_page_layout)
        setupRecyclerView()
        loadData()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        adapter = FeedAdapter()

        recyclerView.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            adapter = this@HomePageActivity.adapter

        }
    }

    private fun loadData() {
        // 模拟数据
        val feedItems = listOf(
            FeedItem.ImageTextItem(
                id = "1",
                avatar = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/avatars/avatar_17.jpg",
                authorName = "旅行达人小美",
                title = "周末好去处",
                content = "发现了一个超美的咖啡馆，环境超级棒！",
                coverImage = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/item_photos/item_photo47.png",
                likes = 123,
                comments = 45
            ),
            FeedItem.ImageTextItem(
                id = "2",
                avatar = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/avatars/avatar_145.jpg",
                authorName = "文学青年",
                title = "在山顶许下的心愿",
                content = "岁月静好，风景刚好。在湖光山色中醒来，感受被自然宠爱的瞬间。#湖光山色",
                coverImage = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/item_photos/item_photo387.png",
                likes = 198,
                comments = 32
            ),
            FeedItem.ImageTextItem(
                id = "3",
                avatar = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/avatars/avatar_156.jpg",
                authorName = "勇敢的心",
                title = "用爱去感受每一个角落",
                content = "一次心灵的洗涤，日落黄昏时的浪漫。旅行是最好的解药，听风的声音。",
                coverImage = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/item_photos/item_photo408.png",
                likes = 98,
                comments = 32
            ),
            FeedItem.ImageTextItem(
                id = "4",
                avatar = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/avatars/avatar_139.jpg",
                authorName = "纸上谈兵",
                title = "人生就是一场边走边爱的旅行",
                content = "山川湖海的温柔馈赠，让心灵松绑，享受片刻的宁静",
                coverImage = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/item_photos/item_photo376.png",
                likes = 98,
                comments = 32
            ),
            FeedItem.ImageTextItem(
                id = "5",
                avatar = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/avatars/avatar_17.jpg",
                authorName = "旅行达人小美",
                title = "周末好去处",
                content = "发现了一个超美的咖啡馆，环境超级棒！",
                coverImage = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/item_photos/item_photo486.png",
                likes = 98,
                comments = 32
            ),
            FeedItem.ImageTextItem(
                id = "6",
                avatar = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/avatars/avatar_17.jpg",
                authorName = "旅行达人小美",
                title = "周末好去处",
                content = "发现了一个超美的咖啡馆，环境超级棒！",
                coverImage = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/item_photos/item_photo487.png",
                likes = 98,
                comments = 32
            ),
            FeedItem.ImageTextItem(
                id = "7",
                avatar = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/avatars/avatar_17.jpg",
                authorName = "旅行达人小美",
                title = "周末好去处",
                content = "发现了一个超美的咖啡馆，环境超级棒！",
                coverImage = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/item_photos/item_photo47.png",
                likes = 98,
                comments = 32
            ),
            FeedItem.ImageTextItem(
                id = "8",
                avatar = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/avatars/avatar_17.jpg",
                authorName = "旅行达人小美",
                title = "周末好去处",
                content = "发现了一个超美的咖啡馆，环境超级棒！",
                coverImage = "https://lf3-static.bytednsdoc.com/obj/eden-cn/219eh7pbyphrnuvk/college_training_camp/item_photos/item_photo47.png",
                likes = 98,
                comments = 32
            ),
        )

        adapter.submitList(feedItems)
    }
}