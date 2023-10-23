import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;

public class Audio  {
    public static void playSound(){
        Thread soundThread = new Thread(() -> {
            try {
                File soundFile = new File("sound.wav");
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);

                Clip clip = AudioSystem.getClip();

                clip.open(audioIn);

                clip.start();

                Thread.sleep(clip.getMicrosecondLength() / 1000);

                clip.close();
            } catch (Exception e) {
                throw new RuntimeException("Error playing audio file. " +e.getMessage(), e);
            }
        });

        soundThread.start();
    }
}
