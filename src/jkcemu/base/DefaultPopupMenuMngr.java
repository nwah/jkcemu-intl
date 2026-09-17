/*
 * (c) 2022 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Manager fuer das Standard-PopupMenu
 */

package jkcemu.base;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.text.JTextComponent;
import jkcemu.lang.LangUtil;
import jkcemu.text.TextUtil;


public class DefaultPopupMenuMngr implements ActionListener, MouseListener
{
  private static DefaultPopupMenuMngr instance;

  private JPopupMenu     mnuPopup;
  private JMenuItem      mnuCut;
  private JMenuItem      mnuCopy;
  private JMenuItem      mnuPaste;
  private JMenuItem      mnuSelectAll;
  private JTextComponent textComp;


  public synchronized static DefaultPopupMenuMngr getSharedInstance()
  {
    if( instance == null ) {
      instance = new DefaultPopupMenuMngr();
    }
    return instance;
  }


	/* --- ActionListener --- */

  @Override
  public void actionPerformed( ActionEvent e )
  {
    final JTextComponent c = this.textComp;
    if( c != null ) {
      Object src = e.getSource();
      if( src == this.mnuCut ) {
	c.cut();
      } else if( src == this.mnuCopy ) {
	c.copy();
      } else if( src == this.mnuPaste ) {
	c.paste();
      } else if( src == this.mnuSelectAll ) {
	EventQueue.invokeLater(
			new Runnable()
			{
			  @Override
			  public void run()
			  {
			    c.requestFocus();
			    c.selectAll();
			  }
			} );
      }
    }
  }


	/* --- MouseListener --- */

  @Override
  public void mouseClicked( MouseEvent e )
  {
    if( showPopupMenu( e ) ) {
      e.consume();
    }
  }

  @Override
  public void mouseEntered( MouseEvent e )
  {
    // leer
  }

  @Override
  public void mouseExited( MouseEvent e )
  {
    // leer
  }

  @Override
  public void mousePressed( MouseEvent e )
  {
    if( showPopupMenu( e ) )
      e.consume();
  }

  @Override
  public void mouseReleased( MouseEvent e )
  {
    if( showPopupMenu( e ) )
      e.consume();
  }


	/* --- private Methoden --- */

  /*
   * Das Standard-PopupMenu wird nur angezeigt,
   * wenn es sich um eine Text-Komponente handelt,
   * die keinen weiteren MouseListener
   * an einer JKCEMU-Klasse hat.
   */
  private boolean showPopupMenu( MouseEvent e )
  {
    boolean rv = false;
    if( e != null ) {
      if( e.isPopupTrigger() ) {
	Component c = e.getComponent();
	if( c != null ) {
	  if( c.isEnabled() && (c instanceof JTextComponent) ) {
	    MouseListener[] listeners = c.getMouseListeners();
	    if( listeners != null ) {
	      int n = 0;
	      for( MouseListener l : listeners ) {
		if( (l != this)
		    && l.getClass().getName().startsWith( "jkcemu." ) )
		{
		  n++;
		}
	      }
	      if( n == 0 ) {
		final JTextComponent tc = (JTextComponent) c;
		this.textComp      = tc;
		final int selStart = this.textComp.getSelectionStart();
		final int selEnd   = this.textComp.getSelectionEnd();
		boolean editable   = this.textComp.isEditable();
		boolean selected   = (selStart >= 0) && (selEnd > selStart);
		boolean pasteable  = false;
		if( editable ) {
		  try {
		    Toolkit tk = c.getToolkit();
		    if( tk != null ) {
		      pasteable = tk.getSystemClipboard()
					.isDataFlavorAvailable(
                                        	DataFlavor.stringFlavor );
		    }
		  }
		  catch( Exception ex ) {}
		}
		this.mnuCut.setEnabled( editable && selected );
		this.mnuCopy.setEnabled( selected );
		this.mnuPaste.setEnabled( pasteable );
		this.mnuSelectAll.setEnabled(
				TextUtil.hasText( this.textComp ) );
		this.mnuCut.setVisible( editable );
		this.mnuPaste.setVisible( editable );
		this.mnuPopup.show( c, e.getX(), e.getY() );
		this.textComp.requestFocus();
		this.textComp.select( selStart, selEnd );
		rv = true;

		/*
		 * Durch den Fokuswechsel kann die Selektion verlorengehen.
		 * Deshalb wird diese hier wieder hergestellt.
		 */
		EventQueue.invokeLater(
				new Runnable()
				{
				  @Override
				  public void run()
				  {
				    tc.select( selStart, selEnd );
				  }
				} );
	      }
	    }
	  }
	}
      }
    }
    return rv;
  }


	/* --- Konstruktor --- */

  private DefaultPopupMenuMngr()
  {
    this.mnuCut       = GUIFactory.createMenuItem(
		LangUtil.getText( EmuUtil.TEXT_CUT ) );
    this.mnuCopy      = GUIFactory.createMenuItem(
		LangUtil.getText( EmuUtil.TEXT_COPY ) );
    this.mnuSelectAll = GUIFactory.createMenuItem(
		LangUtil.getText( EmuUtil.TEXT_SELECT_ALL ) );
    this.mnuPaste     = GUIFactory.createMenuItem(
		LangUtil.getText( EmuUtil.TEXT_PASTE ) );
    this.mnuCut.addActionListener( this );
    this.mnuCopy.addActionListener( this );
    this.mnuPaste.addActionListener( this );
    this.mnuSelectAll.addActionListener( this );
    this.mnuPopup = GUIFactory.createPopupMenu();
    this.mnuPopup.add( this.mnuCut );
    this.mnuPopup.add( this.mnuCopy );
    this.mnuPopup.add( this.mnuPaste );
    this.mnuPopup.addSeparator();
    this.mnuPopup.add( this.mnuSelectAll );
  }
}
