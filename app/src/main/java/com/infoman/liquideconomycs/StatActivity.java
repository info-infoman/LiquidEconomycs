package com.infoman.liquideconomycs;

import android.graphics.Color;
import android.os.Bundle;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

public class StatActivity extends AppCompatActivity {
    private Core app;
    HorizontalBarChart h_chartBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        app = (Core) getApplicationContext();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.stat_activity);

        ArrayList data, dataSet, lableCart;
        data = app.getStat();
        dataSet = new ArrayList<>();
        lableCart = new ArrayList<>();



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

        Description description = new Description();
        description.setText(getResources().getString(R.string.description_stat)+ " " + totalCount);
        description.setTextColor(Color.RED);
        description.setTextSize(14);

        BarDataSet barSet = new BarDataSet(dataSet, "");
        BarData barData = new BarData(barSet);
        h_chartBar = findViewById(R.id.h_chartBar);
        h_chartBar.setData(barData);
        h_chartBar.setVisibleXRangeMaximum(15);
        h_chartBar.animateY(2000);
        h_chartBar.setDescription(description);
        h_chartBar.setFitBars(true);
        Legend legend = h_chartBar.getLegend();
        legend.setEnabled(true);
        XAxis xAxis = h_chartBar.getXAxis();
        xAxis.setLabelCount(app.maxAge);
        xAxis.setTextColor(Color.RED);
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(xAxis.getPosition().BOTTOM);
        h_chartBar.getXAxis().setValueFormatter(new IndexAxisValueFormatter(lableCart));
    }
}
