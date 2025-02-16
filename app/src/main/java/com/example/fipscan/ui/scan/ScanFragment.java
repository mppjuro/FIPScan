package com.example.fipscan.ui.scan;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.fipscan.databinding.FragmentScanBinding;

public class ScanFragment extends Fragment {

    private FragmentScanBinding binding;
    private EditText nameInput, albuminInput, globulinInput;
    private TextView ratioText;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentScanBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        nameInput = binding.editTextName;
        albuminInput = binding.editTextAlbumin;
        globulinInput = binding.editTextGlobulin;
        ratioText = binding.textViewRatio;
        Button calculateButton = binding.buttonCalculate;
        Button captureButton = binding.buttonCapture;

        calculateButton.setOnClickListener(v -> calculateRatio());
        captureButton.setOnClickListener(v -> captureImage());

        return root;
    }

    private void calculateRatio() {
        try {
            float albumin = Float.parseFloat(albuminInput.getText().toString());
            float globulin = Float.parseFloat(globulinInput.getText().toString());
            if (globulin != 0) {
                float ratio = albumin / globulin;
                ratioText.setText("Stosunek A/G: " + ratio);
            } else {
                ratioText.setText("Globuliny nie mogą być równe 0.");
            }
        } catch (NumberFormatException e) {
            ratioText.setText("Nieprawidłowe wartości.");
        }
    }

    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
