package tw.com.bte.launcher4;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SamplePresentation extends Presentation {

    private LinearLayout mLayout;
    private TextView mText;

    public SamplePresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the content view to the custom layout
        setContentView(R.layout.sample_presentation);
    }
}
