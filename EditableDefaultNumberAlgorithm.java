package custom.rulealgorithm;

import wt.enterprise.EnterpriseHelper;
import wt.inf.container.WTContainerRef;
import wt.rule.algorithm.RuleAlgorithm;
import wt.util.WTException;

/**
 * Number generator algorithm that allows user override.
 *
 * Mapping convention (when you configure the number generator):
 *  - paramArrayOfobject[0] => user-entered number (if any)  <-- important to wire
 *  - paramArrayOfobject[1] => prefix (optional)
 *  - paramArrayOfobject[2] => other params...
 */
public class EditableDefaultNumberAlgorithm implements RuleAlgorithm {

    @Override
    public Object calculate(Object[] params, WTContainerRef containerRef) throws WTException {
        try {
            // 1) check for user-supplied value (manual override)
            if (params != null && params.length > 0 && params[0] != null) {
                String userVal = params[0].toString().trim();
                if (!userVal.isEmpty()) {
                    // return exactly what user typed (trusting user)
                    return userVal;
                }
            }

            // 2) No user override -> generate default sequence
            String prefix = null;
            if (params != null && params.length > 1 && params[1] != null) {
                prefix = params[1].toString();
            }

            // Use EnterpriseHelper.getNumber(...) as simple sequence helper
            // If you need a different format/logic, replace this with your logic.
            String generated;
            if (prefix != null && !prefix.isEmpty()) {
                generated = prefix + EnterpriseHelper.getNumber(prefix);
            } else {
                generated = EnterpriseHelper.getNumber("");
            }

            return generated;

        } catch (Exception e) {
            throw new WTException("EditableDefaultNumberAlgorithm error: " + e.getMessage());
        }
    }
}
