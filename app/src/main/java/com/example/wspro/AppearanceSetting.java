package com.example.wspro;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public final class AppearanceSetting {

    private static final String[] MODES = {"system", "light", "dark"};

    private AppearanceSetting() { }

    public static void bind(Activity activity, Spinner spinner, SupabaseClient client) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                activity, R.array.appearance_modes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String currentMode = client.getAppearanceMode();
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(currentMode)) {
                spinner.setSelection(i, false);
                break;
            }
        }

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       android.view.View view, int position, long id) {
                if (position < 0 || position >= MODES.length) return;
                String mode = MODES[position];
                if (!mode.equals(client.getAppearanceMode())) {
                    client.setAppearanceMode(mode);
                    SupabaseClient.applyAppearance(mode);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }
}
