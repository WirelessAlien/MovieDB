/*
 *     This file is part of "ShowCase" formerly Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     ShowCase is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ShowCase is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with "ShowCase".  If not, see <https://www.gnu.org/licenses/>.
 */
package com.wirelessalien.android.moviedb.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

/*
* Credits for this class go to cyrilmottier.
* https://cyrilmottier.com/2013/05/24/pushing-the-actionbar-to-the-next-level/
*/
class NotifyingScrollView : NestedScrollView {
    private var mOnScrollChangedListener: OnScrollChangedListener? = null

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(
        context!!, attributeSet
    )

    constructor(context: Context?, attributeSet: AttributeSet?, defStyle: Int) : super(
        context!!, attributeSet, defStyle
    )

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (mOnScrollChangedListener != null) {
            mOnScrollChangedListener!!.onScrollChanged(t)
        }
    }

    fun setOnScrollChangedListener(listener: OnScrollChangedListener?) {
        mOnScrollChangedListener = listener
    }

    interface OnScrollChangedListener {
        fun onScrollChanged(t: Int)
    }
}
