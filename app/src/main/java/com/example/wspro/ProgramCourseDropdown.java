package com.example.wspro;

import android.content.Context;
import android.widget.ArrayAdapter;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

final class ProgramCourseDropdown {
    private ProgramCourseDropdown() {}

    static void bind(Context context, MaterialAutoCompleteTextView field) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                context, R.array.program_courses, android.R.layout.simple_list_item_1);
        field.setAdapter(adapter);
        field.setThreshold(0);
        field.setOnClickListener(view -> field.showDropDown());
    }
}
