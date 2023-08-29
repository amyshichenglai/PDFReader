package com.example.pdfreader

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class PDFModel {
    private val _pagenum = MutableLiveData(0)

    val pagenum : LiveData<Int>
        get() {
            return _pagenum
        }

    fun nextpage() {
        _pagenum.value = pagenum.value!! + 1
    }

    fun prevpage() {
        _pagenum.value = pagenum.value!! - 1
    }
}