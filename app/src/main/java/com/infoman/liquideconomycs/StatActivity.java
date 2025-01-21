package com.infoman.liquideconomycs;

import android.content.res.Resources;
import android.graphics.Color;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.TextView;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;

public class StatActivity extends AppCompatActivity {
    private Core app;
    HorizontalBarChart h_chartBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        app = (Core) getApplicationContext();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.stat_activity);
        h_chartBar = findViewById(R.id.h_chartBar);
        ArrayList data = app.getStat();
        ArrayList dataSet = new ArrayList<>();
        ArrayList lableCart = new ArrayList<>();

        int totalCount = 0;
        for (int i = 0; i < app.maxAge; i++) {

            int count = (int) data.get(i);
            totalCount = totalCount + count;
            dataSet.add(new BarEntry(i, count));

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.add(Calendar.DAY_OF_MONTH, -i);
            lableCart.add(calendar.get(Calendar.DAY_OF_MONTH)
                    + " " + calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT_FORMAT, new Locale("eng")));
        }

        BarDataSet barSet = new BarDataSet(dataSet, "");
        BarData barData = new BarData(barSet);
        h_chartBar.setData(barData);
        h_chartBar.setVisibleXRangeMaximum(15);
        h_chartBar.animateY(2000);
        Description description = new Description();
        description.setText(getResources().getString(R.string.description_stat)+ " " + totalCount);
        description.setTextColor(Color.RED);
        description.setTextSize(14);
        h_chartBar.setDescription(description);
        h_chartBar.setFitBars(true);
        h_chartBar.getXAxis().setLabelCount(app.maxAge);
        h_chartBar.getXAxis().setTextColor(Color.RED);
        Legend legend = h_chartBar.getLegend();
        legend.setEnabled(true);
        XAxis xAxis = h_chartBar.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(xAxis.getPosition().BOTTOM);
        h_chartBar.getXAxis().setValueFormatter(new IndexAxisValueFormatter(lableCart));
    }
}
