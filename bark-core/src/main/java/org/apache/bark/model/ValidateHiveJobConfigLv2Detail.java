/*
	Copyright (c) 2016 eBay Software Foundation.
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	    http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */
package org.apache.bark.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ValidateHiveJobConfigLv2Detail {
	private int name;
	private double result;

	public ValidateHiveJobConfigLv2Detail()
	{
		;
	}

	public ValidateHiveJobConfigLv2Detail(int name)
	{
		this.name = name;
	}

	public ValidateHiveJobConfigLv2Detail(int name, double result)
	{
		this.name = name;
		this.result = result;
	}

	public int getName() {
		return name;
	}

	public void setName(int name) {
		this.name = name;
	}

	public double getResult() {
		return result;
	}

	public void setResult(double result) {
		this.result = result;
	}




}
