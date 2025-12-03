package com.example.waterfall.activity

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.waterfall.R

class HashTagPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hash_tag_page_layout)
        val hashTagText = intent.getStringExtra("hashTag")
        val hashTagTextView: TextView = findViewById(R.id.hashtagTextView)
        hashTagTextView.text = hashTagText
        val returnIcon: ImageView = findViewById(R.id.return_icon_hashtag_page)
        returnIcon.setOnClickListener {
            finish()
        }
    }
}