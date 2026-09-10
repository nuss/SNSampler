SNSamplerOSCPanel {
	classvar <all;
	var <sampler, <>oscAddr, <>oscCmdPrefix, <>backupBuffersPrefix;
	var <>cmdNameTemplates;
	var widgetNameTemplates, inNames;

	*initClass {
		all = ();
	}

	*new { |sampler, oscAddr, oscCmdPrefix="/sampler", backupBuffersPrefix, cmdTemplates|
		if (sampler.isNil or: { sampler.class != SNSampler }) {
			Error("A new SNSamplerOSCPanel needs an existing SNSampler instance!").throw;
		} {
			if (all[sampler.name].notNil) {
				"A SNSamplerOSCPanel already for SNSampler '%' already exists".format(sampler.name).error;
				^nil;
			} {
				^super.newCopyArgs(sampler, oscAddr, oscCmdPrefix, backupBuffersPrefix).init(cmdTemplates);
			}
		}
	}

	init { |cmdTemplates|
		var wName;

		all.put(sampler.name, this);
		widgetNameTemplates = (
			ins: "%-inBus%",
			buffers: "%-activateBuffer%",
			resetBufs: "%-resetBuffer%",
			bufferStatuses: "%-bufferStaus%",
			resetAll: "%-resetAll",
			startStop: "%-start/Stop",
		);
		if (cmdTemplates.isNil) {
			this.cmdNameTemplates = (
				selectInBus: "%/in_select/%",
				displayInBus: "%/in%",
				selectBuffer: "%/select_buffer/%/1",
				zeroBuffer: "%/zero_buffer/%/1",
				bufferStatus: "%/buffer_status%",
				startStop: "%/start_stop",
				zeroAllBuffers: "%/zero_all"
			)
		} {
			this.cmdTemplates = cmdTemplates
		};
		sampler.controllerKeys = sampler.controllerKeys.add(\osc);

		sampler.numBuffers.do { |i|
			wName = widgetNameTemplates.ins.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, tab: sampler.name, svItems: sampler.inKeys ? [\nil]);
			this.oscAddr !? {
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectInBus.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'set in label', "{ |sv|
				var oscPanel = SNSamplerOSCPanel.all['%'];
				oscPanel.oscAddr !? {
					oscPanel.oscAddr.sendMsg(
						oscPanel.cmdNameTemplates.displayInBus.format(oscPanel.oscCmdPrefix, %), sv.item
					);
					oscPanel.oscAddr.sendMsg(
						oscPanel.cmdNameTemplates.selectInBus.format(oscPanel.oscCmdPrefix, %), sv.input
					);
				}
			}".format(sampler.name, i+1, i+1));
			wName = widgetNameTemplates.buffers.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, \false, tab: sampler.name);
			this.oscAddr !? {
				// weird hack: why do I have to disconnect first???
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'activate buffer for sampling', "{ |sv|
				var sampler = SNSampler.all['%'];
				var oscPanel = SNSamplerOSCPanel.all['%'];
				sampler.prepareRecording(sv.value.asBoolean, %, sampler.ins[CVCenter.at('%').item]);
				oscPanel.oscAddr !? {
					oscPanel.oscAddr.sendMsg('%', sv.input)
				}
			}".format(
				sampler.name, sampler.name, i,
				widgetNameTemplates.ins.format(sampler.name, i+1),
				this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1)
			));
			wName = widgetNameTemplates.resetBufs.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, \false, tab: sampler.name);
			this.oscAddr !? {
				// weird hack: why do I have to disconnect first???
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.zeroBuffer.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'zero buffer', "{ |cv|
				var sampler = SNSampler.all['%'];
				var oscPanel = SNSamplerOSCPanel.all['%'];
				sampler.reset(%);
				oscPanel.oscAddr !? {
					oscPanel.oscAddr.sendMsg('%', cv.input)
				}
			}".format(
				sampler.name, sampler.name, i,
				this.cmdNameTemplates.zeroBuffer.format(this.oscCmdPrefix, i+1)
			));
		};
		wName = widgetNameTemplates.resetAll.format(sampler.name).asSymbol;
		CVCenter.use(wName, \false, tab: sampler.name);
		this.oscAddr !? {
			CVCenter.cvWidgets[wName].oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.zeroAllBuffers.format(this.oscCmdPrefix));
		};
		CVCenter.addActionAt(wName, 'zero all buffers', "{ |cv|
			var sampler = SNSampler.all['%'];
			var oscPanel = SNSamplerOSCPanel.all['%'];
			sampler.reset;
			oscPanel.oscAddr !? {
				sampler.buffers.do { |buf, i|
					oscPanel.oscAddr.sendMsg(oscPanel.cmdNameTemplates.bufferStatus.format(oscPanel.oscCmdPrefix, i+1), 0)
				}
			}
		}".format(sampler.name, sampler.name));
		wName = widgetNameTemplates.startStop.format(sampler.name).asSymbol;
		CVCenter.use(wName, \false, tab: sampler.name);
		CVCenter.cvWidgets[wName].setSoftWithin(0);
		this.oscAddr !? {
			CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.startStop.format(this.oscCmdPrefix))
		};
		CVCenter.addActionAt(wName, 'start/stop sampling', "{ |cv|
			var sampler = SNSampler.all['%'];
			var oscPanel = SNSamplerOSCPanel.all['%'];
			sampler.sample(cv.input.asBoolean);
			oscPanel.oscAddr !? {
				oscPanel.oscAddr.sendMsg(oscPanel.cmdNameTemplates.startStop.format(oscPanel.oscCmdPrefix), cv.input)
			}
		}".format(sampler.name, sampler.name));

		this.prInitController;
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
			sampler.ins.putAll(inPairs);
		}
	}

	prInitController {
		var isSampling = false;

		sampler.controllerKeys_(sampler.controllerKeys.add(\samplerOscPanel));

		sampler.mc.samplingController.put(\samplerOscPanel, { |changer, what|
			isSampling = changer.value;
			this.oscAddr !? {
				if (isSampling) {
					sampler.mc.recBufInsModel.value.keys.do { |n|
						// keys are Symbols!!
						this.oscAddr.sendMsg(
							this.cmdNameTemplates.bufferStatus.format(this.oscCmdPrefix, n.asInteger+1),
							isSampling.asInteger
						);
					}
				} {
					sampler.numBuffers.do { |i|
						CVCenter.at(widgetNameTemplates.buffers.format(sampler.name, i+1).asSymbol).input_(0);
					}
				}
			}
		});

		sampler.mc.statusController.put(\samplerOscPanel, { |changer, what|
			this.oscAddr !? {
				sampler.numBuffers.do { |i|
					this.oscAddr.sendMsg(
						this.cmdNameTemplates.bufferStatus.format(this.oscCmdPrefix, i+1),
						changer.value.includes(i.asSymbol).asInteger
					)
				}
			}
		});

		sampler.mc.insController.put(\samplerOscPanel, { |changer, what|
			var inSelect = sampler.numBuffers.collect { |i| CVCenter.at(widgetNameTemplates.ins.format(sampler.name, i+1).asSymbol) };
			inSelect.do { |sv| sv.items_(changer.value[0]) };
		})
	}
}