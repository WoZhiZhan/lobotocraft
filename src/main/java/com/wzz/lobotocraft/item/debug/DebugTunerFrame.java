//package com.wzz.lobotocraft.item.debug;
//
//import javax.swing.*;
//import java.awt.*;
//
//public class DebugTunerFrame extends JFrame {
//
//    public DebugTunerFrame() {
//        setTitle("T 参数调试器");
//        setSize(300, 160);
//        setLocationRelativeTo(null);
//        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
//
//        JPanel panel = new JPanel(new GridLayout(2, 3, 8, 8));
//        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//
//        // f
//        JLabel fLabel = new JLabel("f:");
//        JSpinner fSpinner = new JSpinner(
//                new SpinnerNumberModel(T.f, -10.0, 10.0, 0.025)
//        );
//        JLabel fValue = new JLabel(String.valueOf(T.f));
//
//        // f2
//        JLabel f2Label = new JLabel("f2:");
//        JSpinner f2Spinner = new JSpinner(
//                new SpinnerNumberModel(T.f2, -10.0, 10.0, 0.025)
//        );
//        JLabel f2Value = new JLabel(String.valueOf(T.f2));
//
//        fSpinner.addChangeListener(e -> {
//            T.f = ((Double) fSpinner.getValue()).floatValue();
//            fValue.setText(String.format("%.3f", T.f));
//        });
//
//        f2Spinner.addChangeListener(e -> {
//            T.f2 = ((Double) f2Spinner.getValue()).floatValue();
//            f2Value.setText(String.format("%.3f", T.f2));
//        });
//
//        JLabel f3Label = new JLabel("f3:");
//        JSpinner f3Spinner = new JSpinner(
//                new SpinnerNumberModel(T.f3, -10.0, 10.0, 0.025)
//        );
//        JLabel f3Value = new JLabel(String.valueOf(T.f3));
//
//        JLabel f4Label = new JLabel("f4:");
//        JSpinner f4Spinner = new JSpinner(
//                new SpinnerNumberModel(T.f4, -10.0, 10.0, 0.025)
//        );
//        JLabel f4Value = new JLabel(String.valueOf(T.f4));
//        f3Spinner.addChangeListener(e -> {
//            T.f3 = ((Double) f3Spinner.getValue()).floatValue();
//            f3Value.setText(String.format("%.3f", T.f3));
//        });
//        f4Spinner.addChangeListener(e -> {
//            T.f4 = ((Double) f4Spinner.getValue()).floatValue();
//            f4Value.setText(String.format("%.3f", T.f4));
//        });
//        panel.add(fLabel);
//        panel.add(fSpinner);
//        panel.add(fValue);
//
//        panel.add(f2Label);
//        panel.add(f2Spinner);
//        panel.add(f2Value);
//
//        panel.add(f3Label);
//        panel.add(f3Spinner);
//        panel.add(f3Value);
//
//        panel.add(f4Label);
//        panel.add(f4Spinner);
//        panel.add(f4Value);
//
//        add(panel);
//    }
//
//    public static void open() {
//        SwingUtilities.invokeLater(() -> new DebugTunerFrame().setVisible(true));
//    }
//}
