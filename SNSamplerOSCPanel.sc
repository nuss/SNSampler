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
		var wToSet;

		all.put(sampler.name, this);
		widgetNameTemplates = (
			ins: "%-inBusses%",
			buffers: "%-activateBuffers%",
			resetBufs: "%-resetBuffers",
			resetAll: "%-resetAll",
			startStop: "%-start/Stop",
		);
		this.cmdNameTemplates = (
			selectInBus: "%/in_select/%",
			displayInBus: "%/in%",
			selectBuffer: "%/select_buffer%",
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
			wName = widgetNameTemplates.ins.format(sampler.name, i+1);
			CVCenter.use(wName, tab: sampler.name, svItems: inKeys ? [\nil]);
			CVCenter.addActionAt(wName.asSymbol, 'set in label', { |sv|
				this.oscAddr !? {
					this.oscAddr.sendMsg(this.cmdNameTemplates.displayInBus.format(this.oscCmdPrefix, i+1), sv.item);
					this.oscAddr.sendMsg(this.cmdNameTemplates.selectInBus.format(this.oscCmdPrefix, i+1), sv.input);
				}
			});
			this.oscAddr !? {
				CVCenter.cvWidgets[wName.asSymbol].oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectInBus.format(this.oscCmdPrefix, i+1));
			};
			wToSet = wName;
			wName = widgetNameTemplates.buffers.format(sampler.name, i+1);
			CVCenter.use(wName, \false, tab: sampler.name);
			CVCenter.addActionAt(wName, 'activate buffer for sampling', { |cv|
				sampler.prepareRecording(cv.value.asBoolean, i, ins[CVCenter.at(wToSet).item]);
				this.oscAddr !? {
					this.oscAddr.sendMsg(this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1), cv.input)
				}
			});
			this.oscAddr !? {
				CVCenter.cvWidgets[wName.asSymbol].oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1));
			}
		};
		CVCenter.use(widgetNameTemplates.resetBufs.format(sampler.name), \false!sampler.numBuffers, tab: sampler.name);
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