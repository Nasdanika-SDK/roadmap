import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;

import javax.imageio.ImageIO;

/**
 * Generates the front cover of the roadmap book, in the layout of the cover of the Nasdanika SDK
 * Runtime book and with the word that sets it apart: Roadmap. The fonts are Noto Sans, read from the
 * Asciidoctor PDF jar (SIL Open Font License), so the cover uses the same family as the headings.
 *
 * <p>
 * The cover is 2232 by 2906 pixels, 189 by 246 mm (7.44 by 9.69 inches) at 300 dpi, the trim size of
 * the PDF:
 *
 * <pre>
 * java Cover.java &lt;asciidoctorj-pdf jar&gt; &lt;logo&gt; &lt;output png&gt;
 * </pre>
 *
 * The build runs it and writes target/manuscript/images/cover.png.
 */
public class Cover {

	static final int WIDTH = 2232;
	static final int HEIGHT = 2906;

	static final Color DEEP = new Color(0x0F, 0x4C, 0x6E);
	static final Color BLUE = new Color(0x1B, 0x8E, 0xC6);
	static final Color INK = new Color(0x22, 0x22, 0x22);
	static final Color MUTED = new Color(0x6B, 0x77, 0x80);
	static final Color WASH = new Color(0xF2, 0xF7, 0xFA);

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.err.println("Usage: java Cover.java <asciidoctorj-pdf jar> <logo> <output png>");
			System.exit(1);
		}
		Font regular;
		Font bold;
		try (JarFile jar = new JarFile(args[0])) {
			regular = font(jar, "notosans-regular-subset.ttf");
			bold = font(jar, "notosans-bold-subset.ttf");
		}
		BufferedImage logo = ImageIO.read(new File(args[1]));

		BufferedImage cover = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = cover.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		// White, with a pale wash at the top and a deep band at the bottom
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setColor(WASH);
		g.fillRect(0, 0, WIDTH, 1592);
		g.setColor(DEEP);
		g.fillRect(0, HEIGHT - 260, WIDTH, 260);
		g.setColor(BLUE);
		g.fillRect(0, HEIGHT - 280, WIDTH, 20);

		// The logo, in a thin circle of the brand blue
		int diameter = 1060;
		int x = (WIDTH - diameter) / 2;
		int y = 241;
		g.setColor(Color.WHITE);
		g.fill(new Ellipse2D.Double(x - 30, y - 30, diameter + 60, diameter + 60));
		var clip = g.getClip();
		g.clip(new Ellipse2D.Double(x + 3, y + 3, diameter - 6, diameter - 6)); // The JPEG is square, the logo is round
		g.drawImage(logo, x, y, diameter, diameter, null);
		g.setClip(clip);
		g.setColor(BLUE);
		g.setStroke(new BasicStroke(10));
		g.draw(new Ellipse2D.Double(x - 30, y - 30, diameter + 60, diameter + 60));

		center(g, bold.deriveFont(150f), DEEP, "Nasdanika SDK Roadmap", 1843);
		center(g, regular.deriveFont(84f), BLUE, "What Comes Next, and How to Take Part", 1990);
		g.setColor(BLUE);
		g.fillRect(WIDTH / 2 - 160, 2068, 320, 8);
		center(g, regular.deriveFont(60f), MUTED, "Plans, modules to try and models to migrate", 2199);
		center(g, bold.deriveFont(84f), INK, "Pavel Vlasov", 2492);
		center(g, regular.deriveFont(52f), Color.WHITE, "roadmap.sdk.nasdanika.org", HEIGHT - 110);
		g.dispose();

		File output = new File(args[2]).getAbsoluteFile();
		output.getParentFile().mkdirs();
		ImageIO.write(cover, "png", output);
		System.out.println("Wrote " + output);
	}

	static Font font(JarFile jar, String name) throws Exception {
		var entry = jar.stream().filter(e -> e.getName().endsWith("/data/fonts/" + name)).findFirst()
				.orElseThrow(() -> new IOException("No " + name + " in " + jar.getName()));
		try (InputStream in = jar.getInputStream(entry)) {
			return Font.createFont(Font.TRUETYPE_FONT, in);
		}
	}

	static void center(Graphics2D g, Font font, Color color, String text, int baseline) {
		g.setFont(font);
		g.setColor(color);
		FontMetrics metrics = g.getFontMetrics();
		g.drawString(text, (WIDTH - metrics.stringWidth(text)) / 2, baseline);
	}

}
