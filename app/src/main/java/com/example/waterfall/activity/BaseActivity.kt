package com.example.waterfall.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.waterfall.R

class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.post_page_layout)
    }
}