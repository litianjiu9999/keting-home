package tv.keting.home;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/** 屏保只显示时间，隔一会儿挪一下位置，避免烧屏。 */
public class ClockDream extends DreamService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock;
    private int hop;
    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            if (clock == null) return;
            hop = (hop + 1) % 4;
            clock.setTranslationX(hop % 2 == 0 ? -48f : 56f);
            clock.setTranslationY(hop < 2 ? -24f : 40f);
            clock.setText(time());
            handler.postDelayed(this, 45_000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(64f);
        clock.setText(time());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(clock, params);
        setContentView(root);
        handler.postDelayed(drift, 45_000);
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(drift);
        clock = null;
        super.onDetachedFromWindow();
    }

    private static String time() {
        Calendar calendar = Calendar.getInstance();
        return String.format(
                Locale.CHINA,
                "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
    }
}
