/*
 * (c) 2024 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * InputStream, der von einem RandomAccessFile liest
 */

package jkcemu.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;


public class RAFInputStream extends InputStream
{
  private RandomAccessFile raf;
  private boolean          keepOpen;


  public RAFInputStream( RandomAccessFile raf, boolean keepOpen )
  {
     this.raf      = raf;
     this.keepOpen = keepOpen;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public int available()
  {
    return 0;
  }


  @Override
  public void close() throws IOException
  {
    if( !this.keepOpen )
      this.raf.close();
  }


  @Override
  public int read() throws IOException
  {
    return this.raf.read();
  }


  @Override
  public int read( byte[] buf ) throws IOException
  {
    return this.raf.read( buf );
  }


  @Override
  public int read( byte[] buf, int offs, int len ) throws IOException
  {
    return this.raf.read( buf, offs, len );
  }


  @Override
  public long skip( long n ) throws IOException
  {
    long rv = 0;
    if( n > 0 ) {
      if( n > Integer.MAX_VALUE ) {
	synchronized( this ) {
	  long len    = this.raf.length();
	  long oldPos = this.raf.getFilePointer();
	  long newPos = oldPos + n;
	  if( newPos > len ) {
	    if( oldPos < len ) {
	      this.raf.seek( len );
	      rv = len - oldPos;
	    }
	  } else {
	    this.raf.seek( newPos );
	    rv = n;
	  }
	}
      } else {
	rv = this.raf.skipBytes( (int) n );
      }
    }
    return rv;
  }
}
