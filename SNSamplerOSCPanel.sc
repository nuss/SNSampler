SNSamplerOSCPanel {
	classvar <all;
	var <sampler, <>oscAddr, <>oscCmdPrefix, <>backupBuffersPrefix, <ins;
	var <>cmdNameTemplates;
	var widgetNameTemplates, inNames;

	*initClass {
		all = ();
	}

	*new { |sampler, oscAddr, oscCmdPrefix="/sampler", backupBuffersPrefix, ins|
		if (sampler.isNil or: { sampler.class != SNSampler }) {
			Error("A new SNSamplerOSCPanel needs an existing SNSampler instance!").throw;
		} {
			if (all[sampler.name].notNil) {
				"A SNSamplerOSCPanel already for SNSampler '%' already exists".format(sampler.name).error;
				^nil;
			} {
				^super.newCopyArgs(sampler, oscAddr, oscCmdPrefix, backupBuffersPrefix, ins).init;
			}
		}
	}

	init {
		var inBusses, inKeys, insSpec = \audioin.asSpec, wName;

		all.put(sampler.name, this);
		widgetNameTemplates = (
			ins: "%-inBusses%",
			buffers: "%-activateBuffers%",
			resetBufs: "%-resetBuffers%",
			resetAll: "%-resetAll",
			startStop: "%-start/Stop",
		);
		this.cmdNameTemplates = (
			selectInBus: "%/in_select/%",
			displayInBus: "%/in%",
			selectBuffer: "%/select_buffer/%/1",
			zeroBuffer: "%/zeroBuffer/%",
			bufferStatus: "%/buffer_status%",
			startStop: "%/start_stop",
			zeroAllBuffers: "%/zero_all"
		);
		ins ?? {
			inBusses = (insSpec.minval..insSpec.maxval);
			inKeys = inBusses.collect(_.asSymbol);
			ins = inBusses.collect { |bus| bus.asSymbol -> bus }.asEvent;
		};
		sampler.controllerKeys = sampler.controllerKeys.add(\osc);

		sampler.numBuffers.do { |i|
			wName = widgetNameTemplates.ins.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, tab: sampler.name, svItems: inKeys ? [\nil]);
			CVCenter.addActionAt(wName, 'set in label', "{ |sv|
				SNSamplerOSCPanel.all['%'].oscAddr !? {
					SNSamplerOSCPanel.all['%'].oscAddr.sendMsg(
						SNSamplerOSCPanel.all['%'].cmdNameTemplates.displayInBus.format(SNSamplerOSCPanel.all['%'].oscCmdPrefix, %), sv.item
					);
					SNSamplerOSCPanel.all['%'].oscAddr.sendMsg(
						SNSamplerOSCPanel.all['%'].cmdNameTemplates.selectInBus.format(SNSamplerOSCPanel.all['%'].oscCmdPrefix, %), sv.input
					);
				}
			}".format(sampler.name, sampler.name, sampler.name, sampler.name, i+1, sampler.name, sampler.name, sampler.name, i+1));
			this.oscAddr !? {
				CVCenter.cvWidgets[wName.asSymbol].oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectInBus.format(this.oscCmdPrefix, i+1));
			};
			wName = widgetNameTemplates.buffers.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, \false, tab: sampler.name);
			CVCenter.addActionAt(wName, 'activate buffer for sampling', "{ |cv|
				SNSampler.all['%'].prepareRecording(cv.value.asBoolean, %, SNSamplerOSCPanel.all['%'].ins[CVCenter.at('%').item]);
			}".format(
				sampler.name, i, sampler.name,
				widgetNameTemplates.ins.format(sampler.name, i+1)
			));
			CVCenter.addActionAt(wName, 'buffer activation feedback', "{ |cv|
				SNSamplerOSCPanel.all['%'].oscAddr !? {
					SNSamplerOSCPanel.all['%'].oscAddr.sendMsg(
						\"%\", cv.input
					)
				}
			}".format(
				sampler.name, sampler.name, this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1)
			));
			this.oscAddr !? {
				CVCenter.cvWidgets[wName.asSymbol].oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1));
			};
			wName = widgetNameTemplates.resetBufs.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, \false, tab: sampler.name);
		};
		CVCenter.use(widgetNameTemplates.resetAll.format(sampler.name), \false, tab: sampler.name);
		CVCenter.use(widgetNameTemplates.startStop.format(sampler.name), \false, tab: sampler.name);
	}

	addIns { |inPairs|
		if (inPairs.size < 2) {
			Error("inPairs must at least consist of one key and one value").throw
		} {
			inPairs = inPairs.asEvent;
			if (inPairs.keys.select { |k| k.class == Symbol }.size < inPairs.keys.size) {
				Error("Keys given inPairs must be symbols!").throw
			};
			if (inPairs.values.select { |v| v.class == Integer }.size < inPairs.values.size) {
				Error("Input channels given in inPairs must be integers!").throw
			};
			sampler.numBuffers.do { |i|
				CVCenter.at(widgetNameTemplates.ins.format(sampler.name, i+1)).items_(
					CVCenter.at(widgetNameTemplates.ins.format(sampler.name, i+1)).items ++ inPairs.keys
				)
			};
			ins.putAll(inPairs);
		}
	}
}