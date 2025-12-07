package com.alexioschiu.waterfall.data

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

class HashtagClickableSpan(
    private val hashTag: String,
    private val color: Int,
    private val onClick: (String) -> Unit
) : ClickableSpan() {
    override fun onClick(widget: View) {
        // 处理点击事件
        onClick(hashTag)
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        // 添加hashtag文本颜色
        ds.color = color
        ds.isUnderlineText = false
    }
}