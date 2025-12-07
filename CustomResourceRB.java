package custom;

import wt.util.resource.RBComment;
import wt.util.resource.RBEntry;
import wt.util.resource.RBPseudo;
import wt.util.resource.RBUUID;
import wt.util.resource.WTListResourceBundle;
import wt.util.resource.WTListResourceBundle;

@RBUUID("custom.CustomResourceRB")
public class CustomResourceRB extends WTListResourceBundle {

	
	@RBEntry("Link Part and  new Document")
	public static final String PRIVATE_CONSTANT_1 = "PartCustomAction.linkPartToDocument.description";

	@RBEntry("Link Part and new Document")
	public static final String PRIVATE_CONSTANT_2 = "PartCustomAction.linkPartToDocument.title";
	
	
	
	@RBEntry("Link Part and existing Document")
	public static final String PRIVATE_CONSTANT_3 = "PartCustomAction.linkPartToExistingDocument.description";

	@RBEntry("Link Part and existing Document")
	public static final String PRIVATE_CONSTANT_4 = "PartCustomAction.linkPartToExistingDocument.title";

}
