package com.yellowko.GlobalDanmaku;

import android.util.Log;

import java.util.ArrayList;

public class VideoList extends ArrayList {

    public void clearFrountVideoList(int index){
        for(int i = 0; i < index; i++){
            this.remove(i);
        }
    }

    public void exchange(int src,int dst){
        this.add(dst,this.get(src));
        this.add(src+1,this.get(dst+1));
        this.remove(src+1);
        this.remove(dst+1);
    }

    public void moveTo(int src,int dst){
        this.add(dst,this.get(src));
        this.remove(src);
    }
}

