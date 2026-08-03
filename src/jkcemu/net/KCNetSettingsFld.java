/*
 * (c) 2011-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Komponente fuer die KCNet-Einstellungen
 */

package jkcemu.net;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EventObject;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.base.UserInputException;
import jkcemu.settings.AbstractSettingsFld;
import jkcemu.settings.SettingsFrm;


public class KCNetSettingsFld
			extends AbstractSettingsFld
			implements DocumentListener
{
  private JTextField fldIpAddr;
  private JTextField fldSubnetMask;
  private JTextField fldGateway;
  private JTextField fldDNSServer;
  private JCheckBox  cbAutoConfig;
  private JButton    btnShowNetConfig;


  public KCNetSettingsFld(
		SettingsFrm settingsFrm,
		String      propPrefix )
  {
    super( settingsFrm, propPrefix );
    setLayout( new BorderLayout() );

    JPanel panel = GUIFactory.createPanel( new GridBagLayout() );
    add( GUIFactory.createScrollPane( panel ), BorderLayout.CENTER );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					GridBagConstraints.REMAINDER, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );

    panel.add(
	GUIFactory.createLabel(
		"Beim \"Einschalten\" KCNet konfigurieren (optional):" ),
	gbc );

    gbc.insets.left = 50;
    gbc.gridwidth   = 1;
    gbc.gridy++;
    panel.add( GUIFactory.createLabel( "IP-Adresse (d.d.d.d):" ), gbc );
    gbc.gridy++;
    panel.add( GUIFactory.createLabel( "Subnetzmaske (d.d.d.d):" ), gbc );
    gbc.gridy++;
    panel.add( GUIFactory.createLabel( "Gateway (d.d.d.d):" ), gbc );
    gbc.gridy++;
    panel.add( GUIFactory.createLabel( "DNS-Server (d.d.d.d):" ), gbc );

    this.cbAutoConfig = GUIFactory.createCheckBox(
		"IP-Adressen der leer gelassenen Felder"
			+ " automatisch ermitteln",
		true );
    this.cbAutoConfig.addActionListener( this );
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.gridy++;
    panel.add( this.cbAutoConfig, gbc );

    this.btnShowNetConfig = GUIFactory.createButton(
		"Netzwerkkonfiguration des zugrundeliegenden"
			+ " Betriebssystems anzeigem" );
    this.btnShowNetConfig.addActionListener( this );
    gbc.insets.top  = 20;
    gbc.insets.left = 5;
    gbc.gridy++;
    panel.add( this.btnShowNetConfig, gbc );

    this.fldIpAddr  = createJTextField();
    gbc.insets.top  = 5;
    gbc.insets.left = 5;
    gbc.gridwidth   = 1;
    gbc.gridy       = 1;
    gbc.gridx++;
    panel.add( this.fldIpAddr, gbc );

    this.fldSubnetMask = createJTextField();
    gbc.gridy++;
    panel.add( this.fldSubnetMask, gbc );

    this.fldGateway = createJTextField();
    gbc.gridy++;
    panel.add( this.fldGateway, gbc );

    this.fldDNSServer = createJTextField();
    gbc.gridy++;
    panel.add( this.fldDNSServer, gbc );
  }


	/* --- DocumentListener --- */

  public void changedUpdate( DocumentEvent e )
  {
    fireDataChanged();
  }


  public void insertUpdate( DocumentEvent e )
  {
    fireDataChanged();
  }


  public void removeUpdate( DocumentEvent e )
  {
    fireDataChanged();
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void applyInput(
		Properties props,
		boolean    selected ) throws UserInputException
  {
    props.setProperty(
		this.propPrefix + KCNet.PROP_IP_ADDR,
		parseIpAddrText( this.fldIpAddr, "IP-Adresse" ) );
    props.setProperty(
		this.propPrefix + KCNet.PROP_SUBNET_MASK,
		parseIpAddrText( this.fldSubnetMask, "Subnetzmaske" ) );
    props.setProperty(
		this.propPrefix + KCNet.PROP_GATEWAY,
		parseIpAddrText( this.fldGateway, "Gateway" ) );
    props.setProperty(
		this.propPrefix + KCNet.PROP_DNS_SERVER,
		parseIpAddrText( this.fldDNSServer, "DNS-Server" ) );
    EmuUtil.setProperty(
		props,
		this.propPrefix + KCNet.PROP_AUTOCONFIG,
		this.cbAutoConfig.isSelected() );
  }


  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( src == this.cbAutoConfig ) {
      rv = true;
      fireDataChanged();
    } else if( src == this.btnShowNetConfig ) {
      rv = true;
      showNetConfig();
    }
    return rv;
  }


  @Override
  public void updFields( Properties props )
  {
    this.fldIpAddr.setText(
		EmuUtil.getProperty(
			props,
			this.propPrefix + KCNet.PROP_IP_ADDR ) );

    this.fldSubnetMask.setText(
		EmuUtil.getProperty(
			props,
			this.propPrefix + KCNet.PROP_SUBNET_MASK ) );

    this.fldGateway.setText(
		EmuUtil.getProperty(
			props,
			this.propPrefix + KCNet.PROP_GATEWAY ) );

    this.fldDNSServer.setText(
		EmuUtil.getProperty(
			props,
			this.propPrefix + KCNet.PROP_DNS_SERVER ) );

    this.cbAutoConfig.setSelected(
		EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + KCNet.PROP_AUTOCONFIG,
			KCNet.DEFAULT_AUTOCONFIG ) );
  }


	/* --- private Methoden --- */

  private static void appendIpAddrTo( StringBuilder buf, byte[] addr )
  {
    if( addr != null ) {
      if( addr.length == 4 ) {
	boolean isFirst = true;
	for( byte b : addr ) {
	  if( isFirst ) {
	    isFirst = false;
	  } else {
	    buf.append( '.' );
	  }
	  buf.append( String.format( "%1d", (int) b & 0xFF ) );
	}
      }
    }
  }


  private JTextField createJTextField()
  {
    JTextField fld = GUIFactory.createTextField( 15 );
    Document   doc = fld.getDocument();
    if( doc != null ) {
      doc.addDocumentListener( this );
    }
    return fld;
  }


  private String parseIpAddrText(
			JTextField fld,
			String     fieldName ) throws UserInputException
  {
    String rv = "";
    String s  = fld.getText();
    if( s != null ) {
      s = s.trim();
      if( !s.isEmpty() ) {
	rv = null;
	try {
	  String[] elems = s.split( "\\.", 5 );
	  if( elems != null ) {
	    if( elems.length == 4 ) {
	      int v1 = Integer.parseInt( elems[ 0 ] );
	      int v2 = Integer.parseInt( elems[ 1 ] );
	      int v3 = Integer.parseInt( elems[ 2 ] );
	      int v4 = Integer.parseInt( elems[ 3 ] );
	      if( (v1 >= 0) && (v1 <= 255)
		  && (v2 >= 0) && (v2 <= 255)
		  && (v3 >= 0) && (v3 <= 255)
		  && (v4 >= 0) && (v4 <= 255) )
	      {
		rv = String.format( "%d.%d.%d.%d",v1, v2, v3, v4 );
	      }
	    }
	  }
	}
	catch( NumberFormatException ex ) {}
	catch( PatternSyntaxException ex ) {}
	if( rv == null ) {
	  throw new UserInputException(
				fieldName + ": Ung\u00FCltige Eingabe",
				fieldName );
	}
      }
    }
    return rv;
  }


  private void showNetConfig()
  {
    NetConfig netConfig = NetConfig.readNetConfig( false );

    StringBuilder buf = new StringBuilder( 1024 );
    buf.append( "<html>\n"
	+ "<table border=\"1\">\n"
	+ "<tr>\n"
	+ "<td align=\"left\">Hardwareadresse:</td><td align=\"left\">" );
    byte[] addr = netConfig.getHardwareAddr();
    if( addr != null ) {
      boolean isFirst = true;
      for( byte b : addr ) {
	if( isFirst ) {
	  isFirst = false;
	} else {
	  buf.append( '-' );
	}
	buf.append( String.format( "%02X", (int) b & 0xFF ) );
      }
    }
    buf.append( "</td></tr>\n"
	+ "<tr><td align=\"left\">IP-Adresse:</td><td align=\"left\">" );
    appendIpAddrTo( buf, netConfig.getIpAddr() );
    buf.append( "</td></tr>\n"
	+ "<tr><td align=\"left\">Subnetzmaske:</td><td align=\"left\">" );
    appendIpAddrTo( buf, netConfig.getSubnetMask() );
    buf.append( "</td></tr>\n"
	+ "<tr><td align=\"left\">DNS-Server:</td><td align=\"left\">" );
    appendIpAddrTo( buf, netConfig.getDnsServerIpAddr() );
    buf.append( "</td></tr>\n"
	+ "</table>\n"
	+ "</html>\n" );

    JEditorPane pane = new JEditorPane( "text/html", buf.toString() );
    pane.setEditable( false );
    JOptionPane.showMessageDialog(
		this,
		pane,
		"IPv4-Netzwerkkonfiguration",
		JOptionPane.INFORMATION_MESSAGE );
  }
}
