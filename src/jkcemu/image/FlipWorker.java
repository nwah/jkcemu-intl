/*
 * (c) 2025 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Horizontales oder vertikales Spiegeln eines Bilder
 *
 * Wenn das Bild Transparenz enthaelt,
 * erfolgt die Grauwandlung Pixel fuer Pixel.
 * Da das allerdings eine gewisse Zeit dauerert,
 * wird dies in einem separaten Thread erledigt.
 */

package jkcemu.image;

import java.awt.Graphics;
import java.awt.Transparency;
import java.awt.Window;
import java.awt.image.BufferedImage;
import jkcemu.Main;
import jkcemu.base.CancelableProgressDlg;


public class FlipWorker
		implements CancelableProgressDlg.Progressable, Runnable
{
  public enum Orientation { HORIZONTAL, VERTICAL };

  private BufferedImage         srcImg;
  private BufferedImage         dstImg;
  private BufferedImage         retImg;
  private Orientation           orientation;
  private int                   wImg;
  private int                   hImg;
  private volatile int          progressValue;
  private CancelableProgressDlg dlg;


  public static BufferedImage flip(
				Window        owner,
				BufferedImage srcImg,
				Orientation   orientation )
  {
    BufferedImage retImg = null;
    int           w      = srcImg.getWidth();
    int           h      = srcImg.getHeight();
    if( (w > 0) && (h > 0) ) {
      BufferedImage dstImg = ImageUtil.createCompatibleImage( srcImg, w, h );
      if( dstImg != null ) {
	if( srcImg.getTransparency() == Transparency.OPAQUE ) {
	  Graphics g = dstImg.createGraphics();
	  switch( orientation ) {
	    case HORIZONTAL:
	      g.drawImage( srcImg, w, 0, -w, h, owner );
	      break;
	    case VERTICAL:
	      g.drawImage( srcImg, 0, h, w, -h, owner );
	      break;
	  }
	  g.dispose();
	  retImg = dstImg;
	} else {
	  FlipWorker instance = new FlipWorker(
					srcImg,
					dstImg,
					orientation );
	  instance.dlg = new CancelableProgressDlg(
					owner,
					"Bild spiegeln",
					instance );
	  (new Thread(
		Main.getThreadGroup(),
		instance,
		"JKCEMU flip worker" )).start();
	  instance.dlg.setVisible( true );
	  if( !instance.dlg.wasCancelled() ) {
	    retImg = instance.retImg;
	  }
	}
      }
    }
    return retImg;
  }


	/* --- CancelableProgressDlg.Progressable --- */

  @Override
  public int getProgressMax()
  {
    return this.wImg * this.hImg;
  }

  @Override
  public int getProgressValue()
  {
    return this.progressValue;
  }


	/* --- Runnable --- */

  @Override
  public void run()
  {
    try {
      for( int y = 0; y < this.hImg; y++ ) {
	for( int x = 0; x < this.wImg; x++ ) {
	  if( this.dlg.wasCancelled() ) {
	    break;
	  }
	  switch( this.orientation ) {
	    case HORIZONTAL:
	      this.dstImg.setRGB(
			x,
			y,
			this.srcImg.getRGB( this.wImg - x - 1, y ) );
	      break;
	    case VERTICAL:
	      this.dstImg.setRGB(
			x,
			y,
			this.srcImg.getRGB( x, this.hImg - y - 1 ) );
	      break;
	  }
	  this.progressValue++;
	}
      }
      if( !this.dlg.wasCancelled() ) {
	this.retImg = this.dstImg;
      }
    }
    finally {
      this.dlg.fireProgressFinished();
    }
  }


	/* --- Konstruktor --- */

  private FlipWorker(
		BufferedImage srcImg,
		BufferedImage dstImg,
		Orientation   orientation )
  {
    this.srcImg        = srcImg;
    this.dstImg        = dstImg;
    this.orientation   = orientation;
    this.wImg          = srcImg.getWidth();
    this.hImg          = srcImg.getHeight();
    this.progressValue = 0;
    this.dlg           = null;
    this.retImg        = null;
  }
}
