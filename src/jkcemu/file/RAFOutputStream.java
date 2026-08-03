/*
 * (c) 2024 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * OutputStream, der auf ein RandomAccessFile schreibt
 */

package jkcemu.file;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;


public class RAFOutputStream extends OutputStream
{
  private RandomAccessFile raf;
  private boolean          keepOpen;


  public RAFOutputStream( RandomAccessFile raf, boolean keepOpen )
  {
     this.raf      = raf;
     this.keepOpen = keepOpen;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void close() throws IOException
  {
    flush();
    if( !this.keepOpen ) {
      this.raf.close();
    }
  }


  @Override
  public void flush() throws IOException
  {
    this.raf.getChannel().force( false );
  }


  @Override
  public void write( int b ) throws IOException
  {
    this.raf.write( b );
  }


  @Override
  public void write( byte[] buf ) throws IOException
  {
    this.raf.write( buf );
  }


  @Override
  public void write( byte[] buf, int offs, int len ) throws IOException
  {
    this.raf.write( buf, offs, len );
  }
}
