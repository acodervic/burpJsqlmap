package my;

public class Cookie {
	String key;
	String value;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String val) {
		this.value = val;
	}

	public Cookie(String key, String val) {
		super();
		this.key = key;
		this.value = val;
	}

}
