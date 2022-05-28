package com.example.myapplication3;

public class SampleData {
    private String sSubText;
    private String sTitle;
    private String sText;

    public SampleData(String sSubText, String sTitle, String sText){
        this.sSubText = sSubText;
        this.sTitle = sTitle;
        this.sText = sText;
    }

    public String getSubText()
    {
        return this.sSubText;
    }

    public String getTitle()
    {
        return this.sTitle;
    }

    public String getText()
    {
        return this.sText;
    }
}