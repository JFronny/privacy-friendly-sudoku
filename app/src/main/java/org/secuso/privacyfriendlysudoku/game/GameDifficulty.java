package org.secuso.privacyfriendlysudoku.game;

import android.support.annotation.StringRes;

import java.util.LinkedList;

import org.secuso.privacyfriendlysudoku.ui.view.R;

/**
 * Created by Chris on 18.11.2015.
 */
public enum GameDifficulty {

    Unspecified(R.string.gametype_unspecified),
    Easy(R.string.difficulty_easy),
    Moderate(R.string.difficulty_moderate),
    Hard(R.string.difficulty_hard);

    private int resID;

    GameDifficulty(@StringRes int resID) {
        //getResources().getString(resID);
        this.resID = resID;
    }

    public int getStringResID() {
        return resID;
    }

    public static LinkedList<GameDifficulty> getValidDifficultyList() {
        LinkedList<GameDifficulty> validList = new LinkedList<>();
        validList.add(Easy);
        validList.add(Moderate);
        validList.add(Hard);
        return validList;
    }

}