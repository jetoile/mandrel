package io.mandrel.data.source;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class JdbcSource extends Source {

	private static final long serialVersionUID = -4979543740004679462L;

	private String query;
	private String url;

	public void register(EntryListener listener) {

	}

	public boolean check() {
		return true;
	}
}
