package api.models;

import org.eclipse.persistence.descriptors.ClassExtractor;
import org.eclipse.persistence.sessions.Record;
import org.eclipse.persistence.sessions.Session;

public class TransactionClassExtractor extends ClassExtractor {

	@SuppressWarnings("rawtypes")
	@Override
	public Class extractClassFromRow(Record record, Session session) {
		// Never called anyway?
		return null;
	}

}
